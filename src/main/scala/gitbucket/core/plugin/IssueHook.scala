package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, Issue}
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.model.Profile._
import profile.api._

trait IssueHook {

  def created(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = ()
  def addedComment(commentId: Int, content: String, issue: Issue, repository: RepositoryInfo)(
    implicit session: Session,
    context: Context
  ): Unit = ()
  def deletedComment(commentId: Int, issue: Issue, repository: RepositoryInfo)(
    implicit session: Session,
    context: Context
  ): Unit = ()
  def updatedComment(commentId: Int, content: String, issue: Issue, repository: RepositoryInfo)(
    implicit session: Session,
    context: Context
  ): Unit = ()
  def closed(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = ()
  def reopened(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = ()
  def assigned(
    issue: Issue,
    repository: RepositoryInfo,
    assigner: Option[String],
    assigned: Option[String],
    oldAssigned: Option[String]
  )(
    implicit session: Session,
    context: Context
  ): Unit = ()
  def closedByCommitComment(issue: Issue, repository: RepositoryInfo, message: String, pusher: Account)(
    implicit session: Session
  ): Unit = ()

}

trait PullRequestHook extends IssueHook {

  def merged(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = ()

}
