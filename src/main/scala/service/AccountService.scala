package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

trait AccountService {

  def getAccountByUserName(userName: String): Option[Account] =
    Query(Accounts) filter(_.userName is userName.bind) firstOption

}
