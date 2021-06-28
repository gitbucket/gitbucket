package gitbucket.core.api

import gitbucket.core.util.JGitUtil.{CommitInfo, TagInfo}
import gitbucket.core.util.RepositoryName
import org.eclipse.jgit.lib.Ref

case class ApiRefCommit(
  sha: String,
  `type`: String,
  url: ApiPath
)

case class ApiRef(
  ref: String,
  node_id: String = "",
  url: ApiPath,
  `object`: ApiRefCommit,
)

object ApiRef {

  def fromRef(
    repositoryName: RepositoryName,
    commit: Ref
  ): ApiRef =
    ApiRef(
      ref = commit.getName,
      url = ApiPath(s"/${repositoryName.fullName}/refs/${commit.getName}"),
      `object` = ApiRefCommit(
        sha = commit.getObjectId.getName,
        url = ApiPath(s"/${repositoryName.fullName}/commits/${commit.getObjectId.getName}"),
        `type` = "commit"
      )
    )

  def fromTag(
    repositoryName: RepositoryName,
    tagInfo: TagInfo
  ): ApiRef =
    ApiRef(
      ref = s"refs/tags/${tagInfo.name}",
      url = ApiPath(s"/${repositoryName.fullName}/refs/tags/${tagInfo.name}"),
      `object` = ApiRefCommit(
        sha = tagInfo.id,
        url = ApiPath(s"/${repositoryName.fullName}/tags/${tagInfo.id}"),
        `type` = "commit"
      )
    )
}
