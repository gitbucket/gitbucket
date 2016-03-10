package gitbucket.core.service

import java.util.Date

import gitbucket.core.model.{GroupMember, Account, Collaborator, Repository}
import gitbucket.core.model.Profile._
import gitbucket.core.util.{StringUtil, LDAPUtil}
import gitbucket.core.service.SystemSettingsService.SystemSettings
import profile.simple._
import StringUtil._
import org.slf4j.LoggerFactory

import gitbucket.core.servlet.Database._
import io.getquill._

trait AccountService {

  private val logger = LoggerFactory.getLogger(classOf[AccountService])

  def authenticate(settings: SystemSettings, userName: String, password: String): Option[Account] =
    if(settings.ldapAuthentication){
      ldapAuthentication(settings, userName, password)
    } else {
      defaultAuthentication(userName, password)
    }

  /**
   * Authenticate by internal database.
   */
  private def defaultAuthentication(userName: String, password: String) = {
    getAccountByUserName(userName).collect {
      case account if(!account.groupAccount && account.password == sha1(password)) => Some(account)
    } getOrElse None
  }

  /**
   * Authenticate by LDAP.
   */
  private def ldapAuthentication(settings: SystemSettings, userName: String, password: String): Option[Account] = {
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
    db.run(quote { (userName: String, includeRemoved: Boolean) =>
      query[Account].filter { t =>
        if(includeRemoved){
          t.userName == userName
        } else {
          t.userName == userName && t.removed == false
        }
      }
    })(userName, includeRemoved).headOption
  }


  def getAccountsByUserNames(userNames: Set[String], knowns:Set[Account], includeRemoved: Boolean = false)(implicit s: Session): Map[String, Account] = {
    val map = knowns.map(a => a.userName -> a).toMap
    val needs = userNames -- map.keySet
    if(needs.isEmpty){
      map
    } else {
      map ++ db.run(quote { (userNames: Set[String]) =>
          query[Account].filter { t => userNames.contains(t.userName) && t.removed == false }
      })(userNames.toSet).map { a => a.userName -> a }.toMap
    }
  }

  def getAccountByMailAddress(mailAddress: String, includeRemoved: Boolean = false): Option[Account] = {
    db.run(quote { (mailAddress: String, includeRemoved: Boolean) =>
      query[Account].filter { t =>
        if(includeRemoved){
          t.mailAddress.toLowerCase == mailAddress.toLowerCase
        } else {
          t.mailAddress.toLowerCase == mailAddress.toLowerCase && t.removed == false
        }
      }
    })(mailAddress, includeRemoved).headOption
  }

  def getAllUsers(includeRemoved: Boolean = true): List[Account] = {
    db.run(
      if(includeRemoved){
        quote { query[Account].sortBy(_.userName) }
      } else {
        quote { query[Account].filter(_.removed == false).sortBy(_.userName) }
      }
    )
  }

  def createAccount(userName: String, password: String, fullName: String, mailAddress: String, isAdmin: Boolean, url: Option[String]): Unit = {
    db.run(quote { query[Account].insert })(Account(
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
    ))
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
    })((
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
    ))
  }

  def updateAvatarImage(userName: String, image: Option[String]): Unit = {
    db.run(quote { (userName: String, image: Option[String]) =>
      query[Account].filter(_.userName == userName).update(_.image -> image)
    })((userName, image))
  }

  def updateLastLoginDate(userName: String): Unit = {
    db.run(quote { (userName: String, lastLoginDate: Option[Date]) =>
      query[Account].filter(_.userName == userName).update(_.lastLoginDate -> lastLoginDate)
    })((userName, Some(currentDate)))
  }

  def createGroup(groupName: String, url: Option[String]): Unit = {
    db.run( quote { query[Account].insert })(List(Account(
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
      removed       = false
    )))
  }

  def updateGroup(groupName: String, url: Option[String], removed: Boolean): Unit = {
    db.run(quote { (groupName: String, url: Option[String], removed: Boolean) =>
      query[Account].filter(_.userName == groupName).update(_.url -> url, _.removed -> removed)
    })(List((groupName, url, removed)))
  }

  def updateGroupMembers(groupName: String, members: List[(String, Boolean)]): Unit = {
    db.run(quote { (groupName: String) => query[GroupMember].filter(_.groupName == groupName).delete })(groupName)

    members.foreach { case (userName, isManager) =>
      db.run(quote { query[GroupMember].insert })(GroupMember(groupName, userName, isManager))
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

  def removeUserRelatedData(userName: String): Unit = {
    db.run(quote { (userName: String) => query[GroupMember].filter(_.userName == userName).delete })(userName)
    db.run(quote { (userName: String) => query[Collaborator].filter(_.collaboratorName == userName).delete })(userName)
    db.run(quote { (userName: String) => query[Repository].filter(_.userName == userName).delete })(userName)
  }

  def getGroupNames(userName: String): List[String] = {
    List(userName) ++ db.run(quote { (userName: String) =>
      query[Collaborator].filter(_.collaboratorName == userName).sortBy(_.userName).map(_.userName)
    })(userName)
  }

}

object AccountService extends AccountService
