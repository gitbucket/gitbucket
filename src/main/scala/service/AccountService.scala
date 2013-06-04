package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

trait AccountService {

  def getAccountByUserName(userName: String): Option[Account] = 
    Query(Accounts) filter(_.userName is userName.bind) firstOption
    
  def getAllUsers(): List[Account] = Query(Accounts) sortBy(_.userName) list
    
  def createAccount(account: Account): Unit = Accounts.* insert account

  def updateAccount(account: Account): Unit = {
    val q = for {
      a <- Accounts if a.userName is account.userName.bind
    } yield a.password ~ a.mailAddress ~ a.userType ~ a.url.? ~ a.registeredDate ~ a.updatedDate ~ a.lastLoginDate.?
    
    q.update(
        account.password, 
        account.mailAddress, 
        account.userType, 
        account.url, 
        account.registeredDate,
        account.updatedDate,
        account.lastLoginDate)
  }
  
}

object AccountService {

  val Normal = 0
  val Administrator = 1

}