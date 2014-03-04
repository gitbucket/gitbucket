package model

trait WebHookComponent extends BasicTemplateComponent { self: Profile =>
  import profile.simple._

  object WebHooks extends Table[WebHook]("WEB_HOOK") with BasicTemplate {
    def url = column[String]("URL")
    def * = userName ~ repositoryName ~ url <> (WebHook, WebHook.unapply _)

    def byPrimaryKey(owner: String, repository: String, url: String) = byRepository(owner, repository) && (this.url is url.bind)
  }
}

case class WebHook(
  userName: String,
  repositoryName: String,
  url: String
)
