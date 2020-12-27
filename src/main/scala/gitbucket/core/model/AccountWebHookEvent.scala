package gitbucket.core.model

trait AccountWebHookEventComponent extends TemplateComponent {
  self: Profile =>

  import profile.api._
  import gitbucket.core.model.Profile.AccountWebHooks

  lazy val AccountWebHookEvents = TableQuery[AccountWebHookEvents]

  class AccountWebHookEvents(tag: Tag)
      extends Table[AccountWebHookEvent](tag, "ACCOUNT_WEB_HOOK_EVENT")
      with BasicTemplate {
    val url = column[String]("URL")
    val event = column[WebHook.Event]("EVENT")

    def * = (userName, url, event).<>((AccountWebHookEvent.apply _).tupled, AccountWebHookEvent.unapply)

    def byAccountWebHook(userName: String, url: String) = (this.userName === userName.bind) && (this.url === url.bind)

    def byAccountWebHook(owner: Rep[String], url: Rep[String]) =
      (this.userName === userName) && (this.url === url)

    def byAccountWebHook(webhook: AccountWebHooks) =
      (this.userName === webhook.userName) && (this.url === webhook.url)

    def byPrimaryKey(userName: String, url: String, event: WebHook.Event) =
      (this.userName === userName.bind) && (this.url === url.bind) && (this.event === event.bind)
  }
}

case class AccountWebHookEvent(
  userName: String,
  url: String,
  event: WebHook.Event
)
