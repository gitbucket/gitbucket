package gitbucket.core.api

import gitbucket.core.util.RepositoryName

/**
 * https://developer.github.com/v3/repos/#get-branch
 * https://developer.github.com/v3/repos/#enabling-and-disabling-branch-protection
 */
case class ApiBranch(name: String, commit: ApiBranchCommit, protection: ApiBranchProtection)(
  repositoryName: RepositoryName
) extends FieldSerializable {
  val _links =
    Map(
      "self" -> ApiPath(s"/api/v3/repos/${repositoryName.fullName}/branches/${name}"),
      "html" -> ApiPath(s"/${repositoryName.fullName}/tree/${name}")
    )
}

case class ApiBranchCommit(sha: String)

case class ApiBranchForList(
  name: String,
  commit: ApiBranchCommit
)
