package gitbucket.core.api

import gitbucket.core.util.JGitUtil.TagInfo
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
    ref: Ref
  ): ApiRef =
    ApiRef(
      ref = ref.getName,
      url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/git/${ref.getName}"),
      `object` = ApiRefCommit(
        sha = ref.getObjectId.getName,
        url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/git/commits/${ref.getObjectId.getName}"),
        `type` = "commit"
      )
    )

  def fromTag(
    repositoryName: RepositoryName,
    tagInfo: TagInfo
  ): ApiRef =
    ApiRef(
      ref = s"refs/tags/${tagInfo.name}",
      url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/git/refs/tags/${tagInfo.name}"),
      `object` = ApiRefCommit(
        sha = tagInfo.objectId,
        url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/git/tags/${tagInfo.objectId}"), // TODO This URL is not yet available?
        `type` = "tag"
      )
    )
}
