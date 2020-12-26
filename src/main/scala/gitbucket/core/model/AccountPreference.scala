package gitbucket.core.model

trait AccountPreferenceComponent { self: Profile =>
  import profile.api._

  lazy val AccountPreferences = TableQuery[AccountPreferences]

  class AccountPreferences(tag: Tag) extends Table[AccountPreference](tag, "ACCOUNT_PREFERENCE") {
    val userName = column[String]("USER_NAME", O PrimaryKey)
    val highlighterTheme = column[String]("HIGHLIGHTER_THEME")
    def * =
      (userName, highlighterTheme).<>(AccountPreference.tupled, AccountPreference.unapply)

    def byPrimaryKey(userName: String): Rep[Boolean] = this.userName === userName.bind
  }
}

case class AccountPreference(
  userName: String,
  highlighterTheme: String = "prettify"
)
