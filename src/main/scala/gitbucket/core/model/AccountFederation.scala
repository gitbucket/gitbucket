package gitbucket.core.model

trait AccountFederationComponent { self: Profile =>
  import profile.api._

  lazy val AccountFederations = TableQuery[AccountFederations]

  class AccountFederations(tag: Tag) extends Table[AccountFederation](tag, "ACCOUNT_FEDERATION") {
    val issuer = column[String]("ISSUER")
    val subject = column[String]("SUBJECT")
    val userName = column[String]("USER_NAME")
    def * = (issuer, subject, userName).<>(AccountFederation.tupled, AccountFederation.unapply)

    def byPrimaryKey(issuer: String, subject: String): Rep[Boolean] =
      (this.issuer === issuer.bind) && (this.subject === subject.bind)
  }
}

case class AccountFederation(issuer: String, subject: String, userName: String)
