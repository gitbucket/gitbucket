package gitbucket.core.model

trait RepositoryWebHookComponent extends TemplateComponent { self: Profile =>
  import profile.api._

  implicit val whContentTypeColumnType =
    MappedColumnType.base[WebHookContentType, String](whct => whct.code, code => WebHookContentType.valueOf(code))

  lazy val RepositoryWebHooks = TableQuery[RepositoryWebHooks]

  class RepositoryWebHooks(tag: Tag) extends Table[RepositoryWebHook](tag, "WEB_HOOK") with BasicTemplate {
    val hookId = column[Int]("HOOK_ID", O AutoInc)
    val url = column[String]("URL")
    val token = column[Option[String]]("TOKEN")
    val ctype = column[WebHookContentType]("CTYPE")
    def * =
      (userName, repositoryName, hookId, url, ctype, token)
        .<>((RepositoryWebHook.apply _).tupled, RepositoryWebHook.unapply)

    def byRepositoryUrl(owner: String, repository: String, url: String) =
      byRepository(owner, repository) && (this.url === url.bind)

    def byId(id: Int) =
      (this.hookId === id.bind)
  }
}

case class RepositoryWebHook(
  userName: String,
  repositoryName: String,
  hookId: Int = 0,
  url: String,
  ctype: WebHookContentType,
  token: Option[String]
) extends WebHook
