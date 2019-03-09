package gitbucket.core.api

case class CreateAWebhookConfig(
  url: String,
  content_type: String = "form",
  secret: Option[String],
  insecure_ssl: Option[String]
)

case class CreateAWebhook(
  name: Option[String],
  config: CreateAWebhookConfig,
  events: Seq[String],
  active: Option[Boolean]
)
