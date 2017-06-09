package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.model.Issue
import gitbucket.core.service.RepositoryService.RepositoryInfo

trait IssueHook {

  def created(issue: Issue, repository: RepositoryInfo)(implicit context: Context): Unit = ()
  def addedComment(commentId: Int, content: String, issue: Issue, repository: RepositoryInfo)(implicit context: Context): Unit = ()
  def closed(issue: Issue, repository: RepositoryInfo)(implicit context: Context): Unit = ()
  def reopened(issue: Issue, repository: RepositoryInfo)(implicit context: Context): Unit = ()

}

trait PullRequestHook extends IssueHook {

  def merged(issue: Issue, repository: RepositoryInfo)(implicit context: Context): Unit = ()

}
