package gitbucket.core.service

import gitbucket.core.model.{GroupMember, Account}
import gitbucket.core.model.Profile._
import gitbucket.core.util.{StringUtil, LDAPUtil}
import gitbucket.core.service.SystemSettingsService.SystemSettings
import profile.simple._
import StringUtil._
import org.slf4j.LoggerFactory
// TODO Why is direct import required?
import gitbucket.core.model.Profile.dateColumnType

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
      case account if(!account.isGroupAccount && account.password == sha1(password)) => Some(account)
    } getOrElse None
  }

  /**
   * Authenticate by LDAP.
   */
  private def ldapAuthentication(settings: SystemSettings, userName: String, password: String)
                                (implicit s: Session): Option[Account] = {
    LDAPUtil.authenticate(settings.ldap.get, userName, password) match {
      case Right(ldapUserInfo) => {
        // Create or update account by LDAP information
        getAccountByUserName(ldapUserInfo.userName, true) match {
          case Some(x) if(!x.isRemoved) => {
            if(settings.ldap.get.mailAttribute.getOrElse("").isEmpty) {
              updateAccount(x.copy(fullName = ldapUserInfo.fullName))
            } else {
              updateAccount(x.copy(mailAddress = ldapUserInfo.mailAddress, fullName = ldapUserInfo.fullName))
            }
            getAccountByUserName(ldapUserInfo.userName)
          }
          case Some(x) if(x.isRemoved)  => {
            logger.info("LDAP Authentication Failed: Account is already registered but disabled.")
            defaultAuthentication(userName, password)
          }
          case None => getAccountByMailAddress(ldapUserInfo.mailAddress, true) match {
            case Some(x) if(!x.isRemoved) => {
              updateAccount(x.copy(fullName = ldapUserInfo.fullName))
              getAccountByUserName(ldapUserInfo.userName)
            }
            case Some(x) if(x.isRemoved)  => {
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

  def getAccountByUserName(userName: String, includeRemoved: Boolean = false)(implicit s: Session): Option[Account] =
    Accounts filter(t => (t.userName === userName.bind) && (t.removed === false.bind, !includeRemoved)) firstOption

  def getAccountByMailAddress(mailAddress: String, includeRemoved: Boolean = false)(implicit s: Session): Option[Account] =
    Accounts filter(t => (t.mailAddress.toLowerCase === mailAddress.toLowerCase.bind) && (t.removed === false.bind, !includeRemoved)) firstOption

  def getAllUsers(includeRemoved: Boolean = true)(implicit s: Session): List[Account] =
    if(includeRemoved){
      Accounts sortBy(_.userName) list
    } else {
      Accounts filter (_.removed === false.bind) sortBy(_.userName) list
    }

  def createAccount(userName: String, password: String, fullName: String, mailAddress: String, isAdmin: Boolean, url: Option[String])
                   (implicit s: Session): Unit =
    Accounts insert Account(
      userName       = userName,
      password       = password,
      fullName       = fullName,
      mailAddress    = mailAddress,
      isAdmin        = isAdmin,
      url            = url,
      registeredDate = currentDate,
      updatedDate    = currentDate,
      lastLoginDate  = None,
      image          = None,
      isGroupAccount = false,
      isRemoved      = false)

  def updateAccount(account: Account)(implicit s: Session): Unit =
    Accounts
      .filter { a =>  a.userName === account.userName.bind }
      .map    { a => (a.password, a.fullName, a.mailAddress, a.isAdmin, a.url.?, a.registeredDate, a.updatedDate, a.lastLoginDate.?, a.removed) }
      .update (
        account.password,
        account.fullName,
        account.mailAddress,
        account.isAdmin,
        account.url,
        account.registeredDate,
        currentDate,
        account.lastLoginDate,
        account.isRemoved)

  def updateAvatarImage(userName: String, image: Option[String])(implicit s: Session): Unit =
    Accounts.filter(_.userName === userName.bind).map(_.image.?).update(image)

  def updateLastLoginDate(userName: String)(implicit s: Session): Unit =
    Accounts.filter(_.userName === userName.bind).map(_.lastLoginDate).update(currentDate)

  def createGroup(groupName: String, url: Option[String])(implicit s: Session): Unit =
    Accounts insert Account(
      userName       = groupName,
      password       = "",
      fullName       = groupName,
      mailAddress    = groupName + "@devnull",
      isAdmin        = false,
      url            = url,
      registeredDate = currentDate,
      updatedDate    = currentDate,
      lastLoginDate  = None,
      image          = None,
      isGroupAccount = true,
      isRemoved      = false)

  def updateGroup(groupName: String, url: Option[String], removed: Boolean)(implicit s: Session): Unit =
    Accounts.filter(_.userName === groupName.bind).map(t => t.url.? -> t.removed).update(url, removed)

  def updateGroupMembers(groupName: String, members: List[(String, Boolean)])(implicit s: Session): Unit = {
    GroupMembers.filter(_.groupName === groupName.bind).delete
    members.foreach { case (userName, isManager) =>
      GroupMembers insert GroupMember (groupName, userName, isManager)
    }
  }

  def getGroupMembers(groupName: String)(implicit s: Session): List[GroupMember] =
    GroupMembers
      .filter(_.groupName === groupName.bind)
      .sortBy(_.userName)
      .list

  def getGroupsByUserName(userName: String)(implicit s: Session): List[String] =
    GroupMembers
      .filter(_.userName === userName.bind)
      .sortBy(_.groupName)
      .map(_.groupName)
      .list

  def removeUserRelatedData(userName: String)(implicit s: Session): Unit = {
    GroupMembers.filter(_.userName === userName.bind).delete
    Collaborators.filter(_.collaboratorName === userName.bind).delete
    Repositories.filter(_.userName === userName.bind).delete
  }

  def getGroupNames(userName: String)(implicit s: Session): List[String] = {
    List(userName) ++
      Collaborators.filter(_.collaboratorName === userName.bind).sortBy(_.userName).map(_.userName).list
  }

}

object AccountService extends AccountService
