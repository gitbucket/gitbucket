package gitbucket.core.api

case class CreateAPullRequest(
  title: String,
  head: String,
  base: String,
  body: Option[String],
  maintainer_can_modify: Option[Boolean],
  draft: Option[Boolean]
)

case class CreateAPullRequestAlt(
  issue: Integer,
  head: String,
  base: String,
  maintainer_can_modify: Option[Boolean]
)

case class UpdateAPullRequest(
  title: Option[String],
  body: Option[String],
  state: Option[String],
  base: Option[String],
  maintainer_can_modify: Option[Boolean],
)
