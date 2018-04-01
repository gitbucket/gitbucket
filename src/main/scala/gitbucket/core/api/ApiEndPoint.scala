package gitbucket.core.api

case class ApiEndPoint(rate_limit_url: ApiPath = ApiPath("/api/v3/rate_limit"))
