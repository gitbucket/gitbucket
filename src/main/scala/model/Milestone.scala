package model

trait MilestoneComponent extends MilestoneTemplateComponent { self: Profile =>
  import profile.simple._

  object Milestones extends Table[Milestone]("MILESTONE") with MilestoneTemplate {
    def title = column[String]("TITLE")
    def description = column[String]("DESCRIPTION")
    def dueDate = column[java.util.Date]("DUE_DATE")
    def closedDate = column[java.util.Date]("CLOSED_DATE")
    def * = userName ~ repositoryName ~ milestoneId ~ title ~ description.? ~ dueDate.? ~ closedDate.? <> (Milestone, Milestone.unapply _)

    def ins = userName ~ repositoryName ~ title ~ description.? ~ dueDate.? ~ closedDate.?
    def byPrimaryKey(owner: String, repository: String, milestoneId: Int) = byMilestone(owner, repository, milestoneId)
    def byPrimaryKey(userName: Column[String], repositoryName: Column[String], milestoneId: Column[Int]) = byMilestone(userName, repositoryName, milestoneId)
  }
}

case class Milestone(
  userName: String,
  repositoryName: String,
  milestoneId: Int,
  title: String,
  description: Option[String],
  dueDate: Option[java.util.Date],
  closedDate: Option[java.util.Date])
