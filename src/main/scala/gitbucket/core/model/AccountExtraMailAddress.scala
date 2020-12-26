package gitbucket.core.model

trait AccountExtraMailAddressComponent { self: Profile =>
  import profile.api._

  lazy val AccountExtraMailAddresses = TableQuery[AccountExtraMailAddresses]

  class AccountExtraMailAddresses(tag: Tag) extends Table[AccountExtraMailAddress](tag, "ACCOUNT_EXTRA_MAIL_ADDRESS") {
    val userName = column[String]("USER_NAME", O PrimaryKey)
    val extraMailAddress = column[String]("EXTRA_MAIL_ADDRESS", O PrimaryKey)
    def * =
      (userName, extraMailAddress).<>(AccountExtraMailAddress.tupled, AccountExtraMailAddress.unapply)
  }
}

case class AccountExtraMailAddress(
  userName: String,
  extraMailAddress: String
)
