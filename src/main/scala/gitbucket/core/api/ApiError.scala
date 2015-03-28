package gitbucket.core.api

case class ApiError(
  message: String,
  documentation_url: Option[String] = None)
