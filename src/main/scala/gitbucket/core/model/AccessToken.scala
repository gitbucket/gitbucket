package gitbucket.core.model


trait AccessTokenComponent { self: Profile =>
  import profile.simple._
  lazy val AccessTokens = TableQuery[AccessTokens]

  class AccessTokens(tag: Tag) extends Table[AccessToken](tag, "ACCESS_TOKEN") {
    val accessTokenId = column[Int]("ACCESS_TOKEN_ID", O AutoInc)
    val userName = column[String]("USER_NAME")
    val tokenHash = column[String]("TOKEN_HASH")
    val note = column[String]("NOTE")
    def * = (accessTokenId, userName, tokenHash, note) <> (AccessToken.tupled, AccessToken.unapply)
  }
}
case class AccessToken(
  accessTokenId: Int = 0,
  userName: String,
  tokenHash: String,
  note: String
)
