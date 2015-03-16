package api

import java.util.Date
import gitbucket.core.util.JGitUtil.CommitInfo

case class ApiPersonIdent(
  name: String,
  email: String,
  date: Date)

object ApiPersonIdent {
  def author(commit: CommitInfo): ApiPersonIdent =
    ApiPersonIdent(
      name  = commit.authorName,
      email = commit.authorEmailAddress,
      date  = commit.authorTime)
  def committer(commit: CommitInfo): ApiPersonIdent =
    ApiPersonIdent(
      name  = commit.committerName,
      email = commit.committerEmailAddress,
      date  = commit.commitTime)
}
