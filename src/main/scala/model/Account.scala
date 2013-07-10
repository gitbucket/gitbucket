package model

import scala.slick.driver.H2Driver.simple._

object Accounts extends Table[Account]("ACCOUNT") {
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def mailAddress = column[String]("MAIL_ADDRESS")
  def password = column[String]("PASSWORD")
  def isAdmin = column[Boolean]("ADMINISTRATOR")
  def url = column[String]("URL")
  def registeredDate = column[java.util.Date]("REGISTERED_DATE")
  def updatedDate = column[java.util.Date]("UPDATED_DATE")
  def lastLoginDate = column[java.util.Date]("LAST_LOGIN_DATE")
  def image = column[String]("IMAGE")
  def * = userName ~ mailAddress ~ password ~ isAdmin ~ url.? ~ registeredDate ~ updatedDate ~ lastLoginDate.? ~ image.? <> (Account, Account.unapply _)
}

case class Account(
    userName: String,
    mailAddress: String,
    password: String,
    isAdmin: Boolean,
    url: Option[String],
    registeredDate: java.util.Date,
    updatedDate: java.util.Date,
    lastLoginDate: Option[java.util.Date],
    image: Option[String]
)
