package model

import scala.slick.driver.H2Driver.simple._

object PullRequests extends Table[PullRequest]("PULL_REQUEST") with IssueTemplate {
  def pullRequestId = column[Int]("PULL_REQUEST_ID")
  def commitId = column[String]("COMMIT_ID")
  def * = pullRequestId ~ userName ~ repositoryName ~ issueId ~ commitId <> (PullRequest, PullRequest.unapply _)

  def autoinc = userName ~ repositoryName ~ issueId ~ commitId returning pullRequestId
  def byPrimaryKey(pullRequestId: Int) = this.pullRequestId is pullRequestId.bind
}

case class PullRequest(
  pullRequestId: Int,
  userName: String,
  repositoryName: String,
  issueId: Int,
  commitId: String)