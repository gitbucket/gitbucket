package model

import scala.slick.driver.H2Driver.simple._

object PullRequests extends Table[PullRequest]("PULL_REQUEST") with IssueTemplate {
  def branch = column[String]("BRANCH")
  def requestUserName = column[String]("REQUEST_USER_NAME")
  def requestRepositoryName = column[String]("REQUEST_REPOSITORY_NAME")
  def requestBranch = column[String]("REQUEST_BRANCH")
  def mergeStartId = column[String]("MERGE_START_ID")
  def mergeEndId = column[String]("MERGE_END_ID")
  def * = userName ~ repositoryName ~ issueId ~ branch ~ requestUserName ~ requestRepositoryName ~ requestBranch ~ mergeStartId.? ~ mergeEndId.? <> (PullRequest, PullRequest.unapply _)

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
  requestBranch: String,
  mergeStartId: Option[String],
  mergeEndId: Option[String])