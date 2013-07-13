package model

import scala.slick.driver.H2Driver.simple._

object PullRequests extends Table[PullRequest]("PULL_REQUEST") with IssueTemplate {
  def requestUserName = column[String]("REQUEST_USER_NAME")
  def requestRepositoryName = column[String]("REQUEST_REPOSITORY_NAME")
  def requestCommitId = column[String]("REQUEST_COMMIT_ID")
  def originBranch = column[String]("ORIGIN_BRANCH")
  def * = userName ~ repositoryName ~ issueId ~ originBranch ~ requestUserName ~ requestRepositoryName ~ requestCommitId <> (PullRequest, PullRequest.unapply _)

  def byPrimaryKey(userName: String, repositoryName: String, issueId: Int) = byIssue(userName, repositoryName, issueId)
  def byPrimaryKey(userName: Column[String], repositoryName: Column[String], issueId: Column[Int]) = byIssue(userName, repositoryName, issueId)
}

case class PullRequest(
  userName: String,
  repositoryName: String,
  issueId: Int,
  originBranch: String,
  requestUserName: String,
  requestRepositoryName: String,
  requestCommitId: String)