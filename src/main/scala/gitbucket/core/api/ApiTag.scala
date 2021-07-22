package gitbucket.core.api

import gitbucket.core.util.RepositoryName

case class ApiTagCommit(
  sha: String,
  url: ApiPath,
  `type`: String
)

case class ApiTag(
  name: String,
  `object`: ApiTagCommit,
  zipball_url: ApiPath,
  tarball_url: ApiPath,
  url: ApiPath,
  ref: String
)

object ApiTag {
  def apply(
    tagName: String,
    repositoryName: RepositoryName,
    commitId: String
  ): ApiTag =
    ApiTag(
      name = tagName,
      `object` = ApiTagCommit(
        sha = commitId,
        url = ApiPath(s"/${repositoryName.fullName}/commits/${commitId}"),
        `type` = "commit"
      ),
      ref = s"refs/tags/$tagName",
      url = ApiPath(s"/${repositoryName.fullName}/tree/${tagName}"),
      zipball_url = ApiPath(s"/${repositoryName.fullName}/archive/${tagName}.zip"),
      tarball_url = ApiPath(s"/${repositoryName.fullName}/archive/${tagName}.tar.gz")
    )
}
