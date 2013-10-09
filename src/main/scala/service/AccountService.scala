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
  private def ldapAuthentication(settings: SystemSettings, userName: String, password: String) = {
    LDAPUtil.authenticate(settings.ldap.get, userName, password) match {
      case Right(mailAddress) => {
        // Create or update account by LDAP information
        getAccountByUserName(userName) match {
          case Some(x) => updateAccount(x.copy(mailAddress = mailAddress))
          case None    => createAccount(userName, "", mailAddress, false, None)
        }
        getAccountByUserName(userName)
      }
      case Left(errorMessage) => {
        logger.info(s"LDAP Authentication Failed: ${errorMessage}")
        defaultAuthentication(userName, password)
      }
    }
  }

  def getAccountByUserName(userName: String): Option[Account] = 
    Query(Accounts) filter(_.userName is userName.bind) firstOption

  def getAccountByMailAddress(mailAddress: String): Option[Account] =
    Query(Accounts) filter(_.mailAddress is mailAddress.bind) firstOption

  def getAllUsers(): List[Account] = Query(Accounts) sortBy(_.userName) list
    
  def createAccount(userName: String, password: String, mailAddress: String, isAdmin: Boolean, url: Option[String]): Unit =
    Accounts insert Account(
      userName       = userName,
      password       = password,
      mailAddress    = mailAddress,
      isAdmin        = isAdmin,
      url            = url,
      registeredDate = currentDate,
      updatedDate    = currentDate,
      lastLoginDate  = None,
      image          = None,
      isGroupAccount = false)

  def updateAccount(account: Account): Unit = 
    Accounts
      .filter { a => a.userName is account.userName.bind }
      .map    { a => a.password ~ a.mailAddress ~ a.isAdmin ~ a.url.? ~ a.registeredDate ~ a.updatedDate ~ a.lastLoginDate.? }
      .update (
        account.password, 
        account.mailAddress, 
        account.isAdmin,
        account.url,
        account.registeredDate,
        currentDate,
        account.lastLoginDate)

  def updateAvatarImage(userName: String, image: Option[String]): Unit =
    Accounts.filter(_.userName is userName.bind).map(_.image.?).update(image)

  def updateLastLoginDate(userName: String): Unit =
    Accounts.filter(_.userName is userName.bind).map(_.lastLoginDate).update(currentDate)

  def createGroup(groupName: String, url: Option[String]): Unit =
    Accounts insert Account(
      userName       = groupName,
      password       = "",
      mailAddress    = groupName + "@devnull",
      isAdmin        = false,
      url            = url,
      registeredDate = currentDate,
      updatedDate    = currentDate,
      lastLoginDate  = None,
      image          = None,
      isGroupAccount = true)

  def updateGroup(groupName: String, url: Option[String]): Unit =
    Accounts.filter(_.userName is groupName.bind).map(_.url.?).update(url)

  def updateGroupMembers(groupName: String, members: List[String]): Unit = {
    Query(GroupMembers).filter(_.groupName is groupName.bind).delete
    members.foreach { userName =>
      GroupMembers insert GroupMember (groupName, userName)
    }
  }

  def getGroupMembers(groupName: String): List[String] =
    Query(GroupMembers)
      .filter(_.groupName is groupName.bind)
      .sortBy(_.userName)
      .map(_.userName)
      .list

  def getGroupsByUserName(userName: String): List[String] =
    Query(GroupMembers)
      .filter(_.userName is userName.bind)
      .sortBy(_.groupName)
      .map(_.groupName)
      .list

}
