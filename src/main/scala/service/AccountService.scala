package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

trait AccountService {

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
