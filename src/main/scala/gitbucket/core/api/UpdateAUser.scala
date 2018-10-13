package gitbucket.core.api

case class UpdateAUser(
  name: Option[String],
  email: Option[String],
  blog: Option[String],
  company: Option[String],
  location: Option[String],
  hireable: Option[Boolean],
  bio: Option[String]
)
