package gitbucket.core.service

import java.util.Date

import gitbucket.core.model.{GroupMember, Account, Collaborator, Repository}
import gitbucket.core.model.Profile._
import gitbucket.core.util.{StringUtil, LDAPUtil}
import gitbucket.core.service.SystemSettingsService.SystemSettings
import profile.simple._
import StringUtil._
import org.slf4j.LoggerFactory
// TODO Why is direct import required?
import gitbucket.core.model.Profile.dateColumnType

import io.getquill._
import io.getquill.naming.SnakeCase
import io.getquill.sources.sql.idiom.H2Dialect

trait AccountService {

  private val logger = LoggerFactory.getLogger(classOf[AccountService])

  def authenticate(settings: SystemSettings, userName: String, password: String)(implicit s: Session): Option[Account] =
    if(settings.ldapAuthentication){
      ldapAuthentication(settings, userName, password)
    } else {
      defaultAuthentication(userName, password)
    }

  /**
   * Authenticate by internal database.
   */
  private def defaultAuthentication(userName: String, password: String)(implicit s: Session) = {
    getAccountByUserName(userName).collect {
      case account if(!account.groupAccount && account.password == sha1(password)) => Some(account)
    } getOrElse None
  }

  /**
   * Authenticate by LDAP.
   */
  private def ldapAuthentication(settings: SystemSettings, userName: String, password: String)(implicit s: Session): Option[Account] = {
    LDAPUtil.authenticate(settings.ldap.get, userName, password) match {
      case Right(ldapUserInfo) => {
        // Create or update account by LDAP information
        getAccountByUserName(ldapUserInfo.userName, true) match {
          case Some(x) if(!x.removed) => {
            if(settings.ldap.get.mailAttribute.getOrElse("").isEmpty) {
              updateAccount(x.copy(fullName = ldapUserInfo.fullName))
            } else {
              updateAccount(x.copy(mailAddress = ldapUserInfo.mailAddress, fullName = ldapUserInfo.fullName))
            }
            getAccountByUserName(ldapUserInfo.userName)
          }
          case Some(x) if(x.removed)  => {
            logger.info("LDAP Authentication Failed: Account is already registered but disabled.")
            defaultAuthentication(userName, password)
          }
          case None => getAccountByMailAddress(ldapUserInfo.mailAddress, true) match {
            case Some(x) if(!x.removed) => {
              updateAccount(x.copy(fullName = ldapUserInfo.fullName))
              getAccountByUserName(ldapUserInfo.userName)
            }
            case Some(x) if(x.removed)  => {
              logger.info("LDAP Authentication Failed: Account is already registered but disabled.")
              defaultAuthentication(userName, password)
            }
            case None => {
              createAccount(ldapUserInfo.userName, "", ldapUserInfo.fullName, ldapUserInfo.mailAddress, false, None)
              getAccountByUserName(ldapUserInfo.userName)
            }
          }
        }
      }
      case Left(errorMessage) => {
        logger.info(s"LDAP Authentication Failed: ${errorMessage}")
        defaultAuthentication(userName, password)
      }
    }
  }

  def getAccountByUserName(userName: String, includeRemoved: Boolean = false): Option[Account] = {
    val r = db.run(
      quote { (userName: String, includeRemoved: Boolean) =>
        query[Account].filter { t =>
          if(includeRemoved){
            t.userName == userName
          } else {
            t.userName == userName && t.removed == false
          }
        }
      }
    )(userName, includeRemoved).headOption

    println("************")
    println(r)
    println("************")

    r
  }


  def getAccountsByUserNames(userNames: Set[String], knowns:Set[Account], includeRemoved: Boolean = false)(implicit s: Session): Map[String, Account] = {
    val map = knowns.map(a => a.userName -> a).toMap
    val needs = userNames -- map.keySet
    if(needs.isEmpty){
      map
    }else{
      map ++ Accounts.filter(t => (t.userName inSetBind needs) && (t.removed === false.bind, !includeRemoved)).list.map(a => a.userName -> a).toMap
    }
  }

  // TODO
  lazy val db = source(new JdbcSourceConfig[H2Dialect, SnakeCase]("db"))

  def getAccountByMailAddress(mailAddress: String, includeRemoved: Boolean = false): Option[Account] = {
    db.run(
      if(includeRemoved) {
        quote { (mailAddress: String) => query[Account].filter { t => t.mailAddress.toLowerCase == mailAddress.toLowerCase } }
      } else {
        quote { (mailAddress: String) => query[Account].filter { t => t.mailAddress.toLowerCase == mailAddress.toLowerCase && t.removed == false } }
      }
    )(mailAddress).headOption
  }

  def getAllUsers(includeRemoved: Boolean = true)(implicit s: Session): List[Account] =
    if(includeRemoved){
      Accounts sortBy(_.userName) list
    } else {
      Accounts filter (_.removed === false.bind) sortBy(_.userName) list
    }

  def createAccount(userName: String, password: String, fullName: String, mailAddress: String, isAdmin: Boolean, url: Option[String])
                   (implicit s: Session): Unit = {
    db.run(quote { query[Account].insert })(List(Account(
      userName       = userName,
      password       = password,
      fullName       = fullName,
      mailAddress    = mailAddress,
      administrator  = isAdmin,
      url            = url,
      registeredDate = currentDate,
      updatedDate    = currentDate,
      lastLoginDate  = None,
      image          = None,
      groupAccount   = false,
      removed        = false
    )))
  }

  def updateAccount(account: Account): Unit = {
    db.run(quote { (userName: String, password: String, fullName: String, mailAddress: String, administrator: Boolean,
                    url: Option[String], registeredDate: Date, updatedDate: Date, lastLoginDate: Option[Date], removed: Boolean) =>
      query[Account].filter(_.userName == userName).update(
        _.password       -> password,
        _.fullName       -> fullName,
        _.mailAddress    -> mailAddress,
        _.administrator  -> administrator,
        _.url            -> url,
        _.registeredDate -> registeredDate,
        _.updatedDate    -> updatedDate,
        _.lastLoginDate  -> lastLoginDate,
        _.removed        -> removed
      )
    })(List((
      account.userName,
      account.password,
      account.fullName,
      account.mailAddress,
      account.administrator,
      account.url,
      account.registeredDate,
      currentDate,
      account.lastLoginDate,
      account.removed
    )))
  }

  def updateAvatarImage(userName: String, image: Option[String]): Unit = {
    db.run(quote { (userName: String, image: Option[String]) =>
      query[Account].filter(_.userName == userName).update(_.image -> image)
    })(List((userName, image)))
  }

  def updateLastLoginDate(userName: String): Unit = {
    db.run(quote { (userName: String, lastLoginDate: Option[Date]) =>
      query[Account].filter(_.userName == userName).update(_.lastLoginDate -> lastLoginDate)
    })(List((userName, Some(currentDate))))
  }

  def createGroup(groupName: String, url: Option[String])(implicit s: Session): Unit =
    Accounts insert Account(
      userName       = groupName,
      password       = "",
      fullName       = groupName,
      mailAddress    = groupName + "@devnull",
      administrator  = false,
      url            = url,
      registeredDate = currentDate,
      updatedDate    = currentDate,
      lastLoginDate  = None,
      image          = None,
      groupAccount  = true,
      removed       = false)

  def updateGroup(groupName: String, url: Option[String], removed: Boolean)(implicit s: Session): Unit = {
    db.run(quote { (groupName: String, url: Option[String], removed: Boolean) =>
      query[Account].filter(_.userName == groupName).update(_.url -> url, _.removed -> removed)
    })(List((groupName, url, removed)))
  }

  def updateGroupMembers(groupName: String, members: List[(String, Boolean)])(implicit s: Session): Unit = {
    GroupMembers.filter(_.groupName === groupName.bind).delete
    members.foreach { case (userName, isManager) =>
      GroupMembers insert GroupMember (groupName, userName, isManager)
    }
  }

  def getGroupMembers(groupName: String): List[GroupMember] = {
    db.run(quote { (groupName: String) =>
      query[GroupMember].filter(_.groupName == groupName).sortBy(_.userName)
    })(groupName)
  }

  def getGroupsByUserName(userName: String): List[String] = {
    db.run(quote { (userName: String) =>
      query[GroupMember].filter(_.userName == userName).sortBy(_.groupName).map(_.groupName)
    })(userName)
  }

  def removeUserRelatedData(userName: String)(implicit s: Session): Unit = {
    // TODO db.transactionはコントローラでやる？
    db.transaction {
      db.run(quote { (userName: String) => query[GroupMember].filter(_.userName == userName).delete })(List(userName))
      db.run(quote { (userName: String) => query[Collaborator].filter(_.collaboratorName == userName).delete })(List(userName))
      db.run(quote { (userName: String) => query[Repository].filter(_.userName == userName).delete })(List(userName))
    }
  }

  def getGroupNames(userName: String): List[String] = {
    List(userName) ++ db.run(quote { (userName: String) =>
      query[Collaborator].filter(_.collaboratorName == userName).sortBy(_.userName).map(_.userName)
    })(userName)
  }

}

object AccountService extends AccountService
