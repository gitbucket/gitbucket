package model

import scala.slick.driver.H2Driver.simple._

object Accounts extends Table[Account]("ACCOUNT") {
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def mailAddress = column[String]("MAIL_ADDRESS")
  def password = column[String]("PASSWORD")
  def isAdmin = column[Boolean]("ADMINISTRATOR")
  def url = column[String]("URL")
  def registeredDate = column[java.sql.Timestamp]("REGISTERED_DATE")	// TODO convert java.util.Date later
  def updatedDate = column[java.sql.Timestamp]("UPDATED_DATE")
  def lastLoginDate = column[java.sql.Timestamp]("LAST_LOGIN_DATE")
  def * = userName ~ mailAddress ~ password ~ isAdmin ~ url.? ~ registeredDate ~ updatedDate ~ lastLoginDate.? <> (Account, Account.unapply _)
}

case class Account(
    userName: String,
    mailAddress: String,
    password: String,
    isAdmin: Boolean,
    url: Option[String],
    registeredDate: java.sql.Timestamp,
    updatedDate: java.sql.Timestamp,
    lastLoginDate: Option[java.sql.Timestamp]
)

class AccountDao {
  import Database.threadLocalSession

  def insert(o: Account): Long = Accounts insert o

  def select(key: String): Option[Account] = Query(Accounts) filter(_.userName is key.bind) firstOption

}
