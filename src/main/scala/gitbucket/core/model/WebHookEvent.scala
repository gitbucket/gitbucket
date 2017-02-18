package gitbucket.core.model

trait WebHookEventComponent extends TemplateComponent { self: Profile =>
  import profile.api._
  import gitbucket.core.model.Profile.WebHooks

  lazy val WebHookEvents = TableQuery[WebHookEvents]

  implicit val typedType = MappedColumnType.base[WebHook.Event, String](_.name, WebHook.Event.valueOf(_))

  class WebHookEvents(tag: Tag) extends Table[WebHookEvent](tag, "WEB_HOOK_EVENT") with BasicTemplate {
    val url = column[String]("URL")
    val event = column[WebHook.Event]("EVENT")
    def * = (userName, repositoryName, url, event) <> ((WebHookEvent.apply _).tupled, WebHookEvent.unapply)

    def byWebHook(owner: String, repository: String, url: String) = byRepository(owner, repository) && (this.url === url.bind)
    def byWebHook(owner: Rep[String], repository: Rep[String], url: Rep[String]) =
      byRepository(userName, repositoryName) && (this.url === url)
    def byWebHook(webhook: WebHooks) =
      byRepository(webhook.userName, webhook.repositoryName) && (this.url === webhook.url)
    def byPrimaryKey(owner: String, repository: String, url: String, event: WebHook.Event) = byWebHook(owner, repository, url) && (this.event === event.bind)
  }
}

case class WebHookEvent(
  userName: String,
  repositoryName: String,
  url: String,
  event: WebHook.Event
)
