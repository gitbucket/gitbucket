package model

import scala.slick.driver.H2Driver.simple._
import model.{BaseTable => Table}

object IssueLabels extends Table[IssueLabel]("ISSUE_LABEL") {
  def issueId = column[Int]("ISSUE_ID")
  def labelId = column[Int]("LABEL_ID")
  def * = base ~ issueId ~ labelId <> (IssueLabel, IssueLabel.unapply _)
}

case class IssueLabel(
  userName: String,
  repositoryName: String,
  issueId: Int,
  labelId: Int)