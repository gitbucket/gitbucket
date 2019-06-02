package gitbucket.core.api
import gitbucket.core.model.RepositoryWebHook
import gitbucket.core.model.WebHook

case class ApiRepositoryWebhook(
  id: Int,
  events: Seq[String],
  config: ApiWebhook
) {
  val active = true
  val name = "web"
}

object ApiRepositoryWebhook {
  def apply(repositoryWebhook: RepositoryWebHook, events: Set[WebHook.Event]): ApiRepositoryWebhook =
    ApiRepositoryWebhook(
      id = repositoryWebhook.webHookId,
      events = events.map { e =>
        e.name
      }.toSeq,
      config = ApiWebhook(repositoryWebhook.url, repositoryWebhook.ctype.ctype)
    )
}
