package model

import scala.slick.driver.H2Driver.simple._

object Accounts extends Table[Account]("ACCOUNT") {
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def mailAddress = column[String]("MAIL_ADDRESS")
  def password = column[String]("PASSWORD")
  def userType = column[Int]("USER_TYPE")
  def url = column[String]("URL")
  def registeredDate = column[java.sql.Date]("REGISTERED_DATE")	// TODO convert java.util.Date later
  def updatedDate = column[java.sql.Date]("UPDATED_DATE")
  def lastLoginDate = column[java.sql.Date]("LAST_LOGIN_DATE")
  def * = userName ~ mailAddress ~ password ~ userType ~ url.? ~ registeredDate ~ updatedDate ~ lastLoginDate.? <> (Account, Account.unapply _)
//  def ins = userName ~ mailAddress ~ password ~ userType ~ url.? ~ registeredDate ~ updatedDate ~ lastLoginDate.? <> ({ t => Account(None, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)}, { (o: Account) => Some((o.userName, o.mailAddress, o.password, o.userType, o.url, o.registeredDate, o.updatedDate, o.lastLoginDate))})
}

case class Account(
    userName: String,
    mailAddress: String,
    password: String,
    userType: Int,
    url: Option[String],
    registeredDate: java.sql.Date,
    updatedDate: java.sql.Date,
    lastLoginDate: Option[java.sql.Date]
)

class AccountDao {
  import Database.threadLocalSession

//  def insert(o: Account): Account = Accounts.ins returning Accounts.* insert o
  def insert(o: Account): Long = Accounts.* insert o

  def select(key: String): Option[Account] = Query(Accounts) filter(_.userName is key.bind) firstOption

}
