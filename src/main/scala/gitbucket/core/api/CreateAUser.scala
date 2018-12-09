package gitbucket.core.api

case class CreateAUser(
  login: String,
  password: String,
  email: String,
  fullName: Option[String],
  isAdmin: Option[Boolean],
  description: Option[String],
  url: Option[String]
)
