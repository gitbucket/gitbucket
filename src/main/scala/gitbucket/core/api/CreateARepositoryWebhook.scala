package gitbucket.core.api

case class CreateARepositoryWebhookConfig(
  url: String,
  content_type: String = "form",
  insecure_ssl: String = "0",
  secret: Option[String]
)

/**
 * https://docs.github.com/en/rest/reference/repos#create-a-repository-webhook
 */
case class CreateARepositoryWebhook(
  name: String = "web",
  config: CreateARepositoryWebhookConfig,
  events: List[String] = List("push"),
  active: Boolean = true
) {
  def isValid: Boolean = {
    config.content_type == "form" || config.content_type == "json"
  }
}

case class UpdateARepositoryWebhook(
  name: String = "web",
  config: CreateARepositoryWebhookConfig,
  events: List[String] = List("push"),
  add_events: List[String] = List(),
  remove_events: List[String] = List(),
  active: Boolean = true
) {
  def isValid: Boolean = {
    config.content_type == "form" || config.content_type == "json"
  }
}
