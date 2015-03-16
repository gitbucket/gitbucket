package gitbucket.core.model

trait AccountComponent { self: Profile =>
  import profile.simple._
  import self._

  lazy val Accounts = TableQuery[Accounts]

  class Accounts(tag: Tag) extends Table[Account](tag, "ACCOUNT") {
    val userName = column[String]("USER_NAME", O PrimaryKey)
    val fullName = column[String]("FULL_NAME")
    val mailAddress = column[String]("MAIL_ADDRESS")
    val password = column[String]("PASSWORD")
    val isAdmin = column[Boolean]("ADMINISTRATOR")
    val url = column[String]("URL")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")
    val lastLoginDate = column[java.util.Date]("LAST_LOGIN_DATE")
    val image = column[String]("IMAGE")
    val groupAccount = column[Boolean]("GROUP_ACCOUNT")
    val removed = column[Boolean]("REMOVED")
    def * = (userName, fullName, mailAddress, password, isAdmin, url.?, registeredDate, updatedDate, lastLoginDate.?, image.?, groupAccount, removed) <> (Account.tupled, Account.unapply)
  }
}

case class Account(
  userName: String,
  fullName: String,
  mailAddress: String,
  password: String,
  isAdmin: Boolean,
  url: Option[String],
  registeredDate: java.util.Date,
  updatedDate: java.util.Date,
  lastLoginDate: Option[java.util.Date],
  image: Option[String],
  isGroupAccount: Boolean,
  isRemoved: Boolean
)
