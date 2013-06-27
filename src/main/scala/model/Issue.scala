package model

import scala.slick.driver.H2Driver.simple._
import model.{BaseTable => Table}

object IssueId extends Table[(String, String, Int)]("ISSUE_ID") {
  def issueId = column[Int]("ISSUE_ID")
  def * = base ~ issueId
}

object Issues extends Table[Issue]("ISSUE") with Functions {
  def issueId = column[Int]("ISSUE_ID")
  def openedUserName = column[String]("OPENED_USER_NAME")
  def milestoneId = column[Int]("MILESTONE_ID")
  def assignedUserName = column[String]("ASSIGNED_USER_NAME")
  def title = column[String]("TITLE")
  def content = column[String]("CONTENT")
  def closed = column[Boolean]("CLOSED")
  def registeredDate = column[java.util.Date]("REGISTERED_DATE")
  def updatedDate = column[java.util.Date]("UPDATED_DATE")
  def * = base ~ issueId ~ openedUserName ~ milestoneId.? ~ assignedUserName.? ~ title ~ content.? ~ closed ~ registeredDate ~ updatedDate <> (Issue, Issue.unapply _)
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