package model

import scala.slick.driver.H2Driver.simple._

object IssueLabels extends Table[IssueLabel]("ISSUE_LABEL") with Functions {
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def repositoryName = column[String]("REPOSITORY_NAME", O PrimaryKey)
  def issueId = column[Int]("ISSUE_ID", O PrimaryKey)
  def labelId = column[Int]("LABEL_ID", O PrimaryKey)
  def * = userName ~ repositoryName ~ issueId ~ labelId <> (IssueLabel, IssueLabel.unapply _)
}

case class IssueLabel(
  userName: String,
  repositoryName: String,
  issueId: Int,
  labelId: Int)