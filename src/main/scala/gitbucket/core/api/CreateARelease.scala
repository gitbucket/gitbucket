package gitbucket.core.api

case class CreateARelease(
  tag_name: String,
  target_commitish: Option[String],
  name: Option[String],
  body: Option[String],
  draft: Option[Boolean],
  prerelease: Option[Boolean]
)
