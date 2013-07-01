package model

import scala.slick.driver.H2Driver.simple._

object IssueId extends Table[(String, String, Int)]("ISSUE_ID") with IssueTemplate {
  def * = userName ~ repositoryName ~ issueId
  def byPrimaryKey(owner: String, repository: String) = byRepository(owner, repository)
}

object Issues extends Table[Issue]("ISSUE") with IssueTemplate with MilestoneTemplate with Functions {
  def openedUserName = column[String]("OPENED_USER_NAME")
  def assignedUserName = column[String]("ASSIGNED_USER_NAME")
  def title = column[String]("TITLE")
  def content = column[String]("CONTENT")
  def closed = column[Boolean]("CLOSED")
  def registeredDate = column[java.util.Date]("REGISTERED_DATE")
  def updatedDate = column[java.util.Date]("UPDATED_DATE")
  def * = userName ~ repositoryName ~ issueId ~ openedUserName ~ milestoneId.? ~ assignedUserName.? ~ title ~ content.? ~ closed ~ registeredDate ~ updatedDate <> (Issue, Issue.unapply _)

  def byPrimaryKey(owner: String, repository: String, issueId: Int) = byIssue(owner, repository, issueId)
}

case class Issue(
    userName: String,
    repositoryName: String,
    issueId: Int,
    openedUserName: String,
    milestoneId: Option[Int],
    assignedUserName: Option[String],
    title: String,
    content: Option[String],
    closed: Boolean,
    registeredDate: java.util.Date,
    updatedDate: java.util.Date)