package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.model.Issue
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.model.Profile._
import profile.api._

trait IssueHook {

  def created(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = ()
  def addedComment(commentId: Int, content: String, issue: Issue, repository: RepositoryInfo)(
    implicit session: Session,
    context: Context
  ): Unit = ()
  def closed(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = ()
  def reopened(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = ()

}

trait PullRequestHook extends IssueHook {

  def merged(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = ()

}
