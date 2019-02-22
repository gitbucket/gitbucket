package gitbucket.core.api

case class CreateAGroup(
  login: String,
  admin: String,
  profile_name: Option[String],
  url: Option[String]
)
