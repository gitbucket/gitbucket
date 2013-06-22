package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

trait AccountService {

  def getAccountByUserName(userName: String): Option[Account] = 
    Query(Accounts) filter(_.userName is userName.bind) firstOption
    
  def getAllUsers(): List[Account] = Query(Accounts) sortBy(_.userName) list
    
  def createAccount(account: Account): Unit = Accounts.* insert account

  def updateAccount(account: Account): Unit = 
    Query(Accounts)
      .filter { a => a.userName is account.userName.bind }
      .map    { a => a.password ~ a.mailAddress ~ a.isAdmin ~ a.url.? ~ a.registeredDate ~ a.updatedDate ~ a.lastLoginDate.? }
      .update (
        account.password, 
        account.mailAddress, 
        account.isAdmin,
        account.url, 
        account.registeredDate,
        account.updatedDate,
        account.lastLoginDate)
  
  def updateLastLoginDate(userName: String): Unit =
    Query(Accounts).filter(_.userName is userName.bind).map(_.lastLoginDate)
      .update(new java.sql.Timestamp(System.currentTimeMillis))
  
}
