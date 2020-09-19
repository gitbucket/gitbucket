package gitbucket.core.api

import gitbucket.core.util.RepositoryName

case class ApiTagCommit(
  sha: String,
  url: ApiPath
)

case class ApiTag(
  name: String,
  commit: ApiTagCommit,
  zipball_url: ApiPath,
  tarball_url: ApiPath
)

object ApiTag {
  def apply(
    tagName: String,
    repositoryName: RepositoryName,
    commitId: String
  ): ApiTag =
    ApiTag(
      name = tagName,
      commit = ApiTagCommit(sha = commitId, url = ApiPath(s"/${repositoryName.fullName}/commits/${commitId}")),
      zipball_url = ApiPath(s"/${repositoryName.fullName}/archive/${tagName}.zip"),
      tarball_url = ApiPath(s"/${repositoryName.fullName}/archive/${tagName}.tar.gz")
    )
}
