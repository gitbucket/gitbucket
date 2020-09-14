package gitbucket.core.model

trait AccountHighlighterComponent { self: Profile =>
  import profile.api._

  lazy val AccountHighlighters = TableQuery[AccountHighlighters]

  class AccountHighlighters(tag: Tag) extends Table[AccountHighlighter](tag, "ACCOUNT_HIGHLIGHTER") {
    val userName = column[String]("USER_NAME", O PrimaryKey)
    val theme = column[String]("THEME")
    def * =
      (userName, theme) <> (AccountHighlighter.tupled, AccountHighlighter.unapply)

    def byPrimaryKey(userName: String): Rep[Boolean] = this.userName === userName.bind
  }
}

case class AccountHighlighter(
  userName: String,
  theme: String = "prettify"
)
