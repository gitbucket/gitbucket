package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import service.SystemSettingsService.SystemSettings
import util.StringUtil._
import model.GroupMember
import scala.Some
import model.Account
import util.LDAPUtil
import org.slf4j.LoggerFactory

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
      case account if(!account.isGroupAccount && account.password == sha1(password)) => Some(account)
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
          case Some(x) if(!x.isRemoved) => {
            updateAccount(x.copy(mailAddress = ldapUserInfo.mailAddress, fullName = ldapUserInfo.fullName))
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

  def getAccountByUserName(userName: String, includeRemoved: Boolean = false): Option[Account] =
    Query(Accounts) filter(t => (t.userName is userName.bind) && (t.removed is false.bind, !includeRemoved)) firstOption

  def getAccountByMailAddress(mailAddress: String, includeRemoved: Boolean = false): Option[Account] =
    Query(Accounts) filter(t => (t.mailAddress.toLowerCase is mailAddress.toLowerCase.bind) && (t.removed is false.bind, !includeRemoved)) firstOption

  def getAllUsers(includeRemoved: Boolean = true): List[Account] =
    if(includeRemoved){
      Query(Accounts) sortBy(_.userName) list
    } else {
      Query(Accounts) filter (_.removed is false.bind) sortBy(_.userName) list
    }
    
  def createAccount(userName: String, password: String, fullName: String, mailAddress: String, isAdmin: Boolean, url: Option[String]): Unit =
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

  def updateAccount(account: Account): Unit =
    Accounts
      .filter { a => a.userName is account.userName.bind }
      .map    { a => a.password ~ a.fullName ~ a.mailAddress ~ a.isAdmin ~ a.url.? ~ a.registeredDate ~ a.updatedDate ~ a.lastLoginDate.? ~ a.removed }
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

  def updateAvatarImage(userName: String, image: Option[String]): Unit =
    Accounts.filter(_.userName is userName.bind).map(_.image.?).update(image)

  def updateLastLoginDate(userName: String): Unit =
    Accounts.filter(_.userName is userName.bind).map(_.lastLoginDate).update(currentDate)

  def createGroup(groupName: String, url: Option[String]): Unit =
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

  def updateGroup(groupName: String, url: Option[String], removed: Boolean): Unit =
    Accounts.filter(_.userName is groupName.bind).map(t => t.url.? ~ t.removed).update(url, removed)

  def updateGroupMembers(groupName: String, members: List[(String, Boolean)]): Unit = {
    Query(GroupMembers).filter(_.groupName is groupName.bind).delete
    members.foreach { case (userName, isManager) =>
      GroupMembers insert GroupMember (groupName, userName, isManager)
    }
  }

  def getGroupMembers(groupName: String): List[GroupMember] =
    Query(GroupMembers)
      .filter(_.groupName is groupName.bind)
      .sortBy(_.userName)
      .list

  def getGroupsByUserName(userName: String): List[String] =
    Query(GroupMembers)
      .filter(_.userName is userName.bind)
      .sortBy(_.groupName)
      .map(_.groupName)
      .list

  def removeUserRelatedData(userName: String): Unit = {
    Query(GroupMembers).filter(_.userName is userName.bind).delete
    Query(Collaborators).filter(_.collaboratorName is userName.bind).delete
    Query(Repositories).filter(_.userName is userName.bind).delete
  }

}

object AccountService extends AccountService
