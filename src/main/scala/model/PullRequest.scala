package model

import scala.slick.driver.H2Driver.simple._

object PullRequests extends Table[PullRequest]("PULL_REQUEST") with IssueTemplate {
  def branch = column[String]("BRANCH")
  def requestUserName = column[String]("REQUEST_USER_NAME")
  def requestRepositoryName = column[String]("REQUEST_REPOSITORY_NAME")
  def requestBranch = column[String]("REQUEST_BRANCH")
  def * = userName ~ repositoryName ~ issueId ~ branch ~ requestUserName ~ requestRepositoryName ~ requestBranch <> (PullRequest, PullRequest.unapply _)

  def byPrimaryKey(userName: String, repositoryName: String, issueId: Int) = byIssue(userName, repositoryName, issueId)
  def byPrimaryKey(userName: Column[String], repositoryName: Column[String], issueId: Column[Int]) = byIssue(userName, repositoryName, issueId)
}

case class PullRequest(
  userName: String,
  repositoryName: String,
  issueId: Int,
  branch: String,
  requestUserName: String,
  requestRepositoryName: String,
  requestBranch: String)