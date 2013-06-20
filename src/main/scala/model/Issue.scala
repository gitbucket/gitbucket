package model

import scala.slick.driver.H2Driver.simple._

object IssueId extends Table[(String, String, Int)]("ISSUE_ID") {
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def repositoryName = column[String]("REPOSITORY_NAME", O PrimaryKey)
  def issueId = column[Int]("ISSUE_ID")
  def * = userName ~ repositoryName ~ issueId
}

object Issues extends Table[Issue]("ISSUE") {
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def repositoryName = column[String]("REPOSITORY_NAME", O PrimaryKey)
  def issueId = column[Int]("ISSUE_ID", O PrimaryKey)
  def openedUserName = column[String]("OPENED_USER_NAME")
  def milestoneId = column[Int]("MILESTONE_ID")
  def assignedUserName = column[String]("ASSIGNED_USER_NAME")
  def title = column[String]("TITLE")
  def content = column[String]("CONTENT")
  def registeredDate = column[java.sql.Date]("REGISTERED_DATE")	// TODO convert java.util.Date later
  def updatedDate = column[java.sql.Date]("UPDATED_DATE")
  def * = userName ~ repositoryName ~ issueId ~ openedUserName ~ milestoneId.? ~ assignedUserName.? ~ title ~ content.? ~ registeredDate ~ updatedDate <> (Issue, Issue.unapply _)
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
    registeredDate: java.sql.Date,
    updatedDate: java.sql.Date)