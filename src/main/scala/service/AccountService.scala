package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

trait AccountService {

  def getAccountByUserId(userId: Long): Option[Account] =
    Query(Accounts) filter(_.userId is userId.bind) firstOption


  def getAccountByUserName(userName: String): Option[Account] =
    Query(Accounts) filter(_.userName is userName.bind) firstOption

}
