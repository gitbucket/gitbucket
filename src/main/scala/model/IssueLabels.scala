package model

import scala.slick.driver.H2Driver.simple._

object IssueLabels extends Table[IssueLabel]("ISSUE_LABEL") with IssueTemplate with LabelTemplate {
  def * = userName ~ repositoryName ~ issueId ~ labelId <> (IssueLabel, IssueLabel.unapply _)
}

case class IssueLabel(
  userName: String,
  repositoryName: String,
  issueId: Int,
  labelId: Int)