package gitbucket.core.api

import gitbucket.core.api.ApiCommitListItem._
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.RepositoryName


/**
 * https://developer.github.com/v3/repos/commits/
 */
case class ApiCommitListItem(
  sha: String,
  commit: Commit,
  author: Option[ApiUser],
  committer: Option[ApiUser],
  parents: Seq[Parent])(repositoryName: RepositoryName) {
  val url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/commits/${sha}")
}

object ApiCommitListItem {
  def apply(commit: CommitInfo, repositoryName: RepositoryName): ApiCommitListItem = ApiCommitListItem(
    sha    = commit.id,
    commit = Commit(
      message   = commit.fullMessage,
      author    = ApiPersonIdent.author(commit),
      committer = ApiPersonIdent.committer(commit)
      )(commit.id, repositoryName),
    author    = None,
    committer = None,
    parents   = commit.parents.map(Parent(_)(repositoryName)))(repositoryName)

  case class Parent(sha: String)(repositoryName: RepositoryName){
    val url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/commits/${sha}")
  }

  case class Commit(
    message: String,
    author: ApiPersonIdent,
    committer: ApiPersonIdent)(sha:String, repositoryName: RepositoryName) {
    val url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/git/commits/${sha}")
  }
}
