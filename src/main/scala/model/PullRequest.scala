package model

import scala.slick.driver.H2Driver.simple._

object PullRequests extends Table[PullRequest]("PULL_REQUEST") with IssueTemplate {
  def pullRequestId = column[Int]("PULL_REQUEST_ID")
  def requestUserName = column[String]("REQUEST_USER_NAME")
  def requestRepositoryName = column[String]("REQUEST_REPOSITORY_NAME")
  def requestCommitId = column[String]("REQUEST_COMMIT_ID")
  def * = pullRequestId ~ userName ~ repositoryName ~ issueId ~ requestUserName ~ requestRepositoryName ~ requestCommitId <> (PullRequest, PullRequest.unapply _)

  def autoinc = userName ~ repositoryName ~ issueId ~ requestUserName ~ requestRepositoryName ~ requestCommitId returning pullRequestId
  def byPrimaryKey(pullRequestId: Int) = this.pullRequestId is pullRequestId.bind
}

case class PullRequest(
  pullRequestId: Int,
  userName: String,
  repositoryName: String,
  issueId: Int,
  requestUserName: String,
  requestRepositoryName: String,
  requestCommitId: String)