package gitbucket.core.api

import gitbucket.core.util.RepositoryName

case class ApiContents(
  `type`: String,
  name: String,
  path: String,
  sha: String,
  content: Option[String],
  encoding: Option[String]
)(repositoryName: RepositoryName, refStr: String) {
  val download_url: Option[ApiPath] = if (`type` == "file") {
    Some(ApiPath(s"/api/v3/repos/${repositoryName.fullName}/raw/${sha}/${path}"))
  } else None

  val html_url = ApiPath(s"/${repositoryName.fullName}/tree/${refStr}/${path}")
  val url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/contents/${path}?ref=${refStr}")

  val _link = Map(
    "self" -> url,
    "html" -> html_url
  )
}
