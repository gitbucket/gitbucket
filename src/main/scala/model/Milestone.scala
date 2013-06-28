package model

import scala.slick.driver.H2Driver.simple._

object Milestones extends Table[Milestone]("MILESTONE") with BasicTemplate with Functions {
  def milestoneId = column[Int]("MILESTONE_ID", O AutoInc)
  def title = column[String]("TITLE")
  def description = column[String]("DESCRIPTION")
  def dueDate = column[java.util.Date]("DUE_DATE")
  def closedDate = column[java.util.Date]("CLOSED_DATE")
  def * = userName ~ repositoryName ~ milestoneId ~ title ~ description.? ~ dueDate.? ~ closedDate.? <> (Milestone, Milestone.unapply _)

  def autoInc = userName ~ repositoryName ~ title ~ description.? ~ dueDate.? ~ closedDate.? returning milestoneId
  // TODO create a template?
  def byPrimaryKey(owner: String, repository: String, milestoneId: Int) =
    byRepository(owner, repository) && (this.milestoneId is milestoneId.bind)
}

case class Milestone(
  userName: String,
  repositoryName: String,
  milestoneId: Int,
  title: String,
  description: Option[String],
  dueDate: Option[java.util.Date],
  closedDate: Option[java.util.Date])
