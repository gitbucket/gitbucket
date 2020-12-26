package gitbucket.core.model

trait AccountWebHookComponent extends TemplateComponent { self: Profile =>
  import profile.api._

  private implicit val whContentTypeColumnType =
    MappedColumnType.base[WebHookContentType, String](whct => whct.code, code => WebHookContentType.valueOf(code))

  lazy val AccountWebHooks = TableQuery[AccountWebHooks]

  class AccountWebHooks(tag: Tag) extends Table[AccountWebHook](tag, "ACCOUNT_WEB_HOOK") with BasicTemplate {
    val url = column[String]("URL")
    val token = column[Option[String]]("TOKEN")
    val ctype = column[WebHookContentType]("CTYPE")
    def * = (userName, url, ctype, token).<>((AccountWebHook.apply _).tupled, AccountWebHook.unapply)

    def byPrimaryKey(userName: String, url: String) = (this.userName === userName.bind) && (this.url === url.bind)
  }
}

case class AccountWebHook(
  userName: String,
  url: String,
  ctype: WebHookContentType,
  token: Option[String]
) extends WebHook
