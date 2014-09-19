package model

trait MilestoneComponent extends TemplateComponent { self: Profile =>
  import profile.simple._
  import self._

  lazy val Milestones = TableQuery[Milestones]

  class Milestones(tag: Tag) extends Table[Milestone](tag, "MILESTONE") with MilestoneTemplate {
    override val milestoneId = column[Int]("MILESTONE_ID", O AutoInc)
    val title = column[String]("TITLE")
    val description = column[String]("DESCRIPTION")
    val dueDate = column[java.util.Date]("DUE_DATE")
    val closedDate = column[java.util.Date]("CLOSED_DATE")
    def * = (userName, repositoryName, milestoneId, title, description.?, dueDate.?, closedDate.?) <> (Milestone.tupled, Milestone.unapply)

    def byPrimaryKey(owner: String, repository: String, milestoneId: Int) = byMilestone(owner, repository, milestoneId)
    def byPrimaryKey(userName: Column[String], repositoryName: Column[String], milestoneId: Column[Int]) = byMilestone(userName, repositoryName, milestoneId)
  }
}

case class Milestone(
  userName: String,
  repositoryName: String,
  milestoneId: Int = 0,
  title: String,
  description: Option[String],
  dueDate: Option[java.util.Date],
  closedDate: Option[java.util.Date]
)
