package gitbucket.core.api

import gitbucket.core.util.RepositoryName

case class ApiContents(
  `type`: String,
  name: String,
  path: String,
  sha: String,
  content: Option[String],
  encoding: Option[String]
)(repositoryName: RepositoryName) {
  val download_url: Option[ApiPath] = if (`type` == "file") {
    Some(ApiPath(s"/api/v3/repos/${repositoryName.fullName}/raw/${sha}/${path}"))
  } else None
}
