package gitbucket.core.api

import gitbucket.core.util.JGitUtil
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.RepositoryName

import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.api.Git

import java.util.Date

/**
 * https://developer.github.com/v3/activity/events/types/#pushevent
 */
case class ApiPushCommit(
  id: String,
  message: String,
  timestamp: Date,
  added: List[String],
  removed: List[String],
  modified: List[String],
  author: ApiPersonIdent,
  committer: ApiPersonIdent)(repositoryName:RepositoryName) extends FieldSerializable {
  val url = ApiPath(s"/${repositoryName.fullName}/commit/${id}")
}

object ApiPushCommit{
  def apply(commit: ApiCommit, repositoryName: RepositoryName): ApiPushCommit = ApiPushCommit(
    id = commit.id,
    message = commit.message,
    timestamp = commit.timestamp,
    added = commit.added,
    removed = commit.removed,
    modified = commit.modified,
    author = commit.author,
    committer = commit.committer)(repositoryName)
  def apply(git: Git, repositoryName: RepositoryName, commit: CommitInfo): ApiPushCommit =
    ApiPushCommit(ApiCommit(git, repositoryName, commit), repositoryName)
}
