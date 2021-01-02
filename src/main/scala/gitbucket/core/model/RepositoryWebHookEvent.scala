package gitbucket.core.model

trait RepositoryWebHookEventComponent extends TemplateComponent { self: Profile =>
  import profile.api._
  import gitbucket.core.model.Profile.RepositoryWebHooks

  lazy val RepositoryWebHookEvents = TableQuery[RepositoryWebHookEvents]

  class RepositoryWebHookEvents(tag: Tag)
      extends Table[RepositoryWebHookEvent](tag, "WEB_HOOK_EVENT")
      with BasicTemplate {
    val url = column[String]("URL")
    val event = column[WebHook.Event]("EVENT")
    def * =
      (userName, repositoryName, url, event).<>((RepositoryWebHookEvent.apply _).tupled, RepositoryWebHookEvent.unapply)

    def byRepositoryWebHook(owner: String, repository: String, url: String) =
      byRepository(owner, repository) && (this.url === url.bind)
    def byRepositoryWebHook(owner: Rep[String], repository: Rep[String], url: Rep[String]) =
      byRepository(userName, repositoryName) && (this.url === url)
    def byRepositoryWebHook(webhook: RepositoryWebHooks) =
      byRepository(webhook.userName, webhook.repositoryName) && (this.url === webhook.url)
    def byPrimaryKey(owner: String, repository: String, url: String, event: WebHook.Event) =
      byRepositoryWebHook(owner, repository, url) && (this.event === event.bind)
  }
}

case class RepositoryWebHookEvent(
  userName: String,
  repositoryName: String,
  url: String,
  event: WebHook.Event
)
