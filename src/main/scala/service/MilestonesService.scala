package service

import model._

trait MilestonesService extends MilestoneComponent with IssueComponent { self: Profile =>
  import profile.simple._
  import Database.threadLocalSession

  def createMilestone(owner: String, repository: String, title: String, description: Option[String],
                      dueDate: Option[java.util.Date]): Unit =
    Milestones.ins insert (owner, repository, title, description, dueDate, None)

  def updateMilestone(milestone: Milestone): Unit =
    Milestones
      .filter (t => t.byPrimaryKey(milestone.userName, milestone.repositoryName, milestone.milestoneId))
      .map    (t => t.title ~ t.description.? ~ t.dueDate.? ~ t.closedDate.?)
      .update (milestone.title, milestone.description, milestone.dueDate, milestone.closedDate)

  def openMilestone(milestone: Milestone): Unit = updateMilestone(milestone.copy(closedDate = None))

  def closeMilestone(milestone: Milestone): Unit = updateMilestone(milestone.copy(closedDate = Some(currentDate)))

  def deleteMilestone(owner: String, repository: String, milestoneId: Int): Unit = {
    Issues.filter(_.byMilestone(owner, repository, milestoneId)).map(_.milestoneId.?).update(None)
    Milestones.filter(_.byPrimaryKey(owner, repository, milestoneId)).delete
  }

  def getMilestone(owner: String, repository: String, milestoneId: Int): Option[Milestone] =
    Query(Milestones).filter(_.byPrimaryKey(owner, repository, milestoneId)).firstOption

  def getMilestonesWithIssueCount(owner: String, repository: String): List[(Milestone, Int, Int)] = {
    val counts = Issues
      .filter  { t => (t.byRepository(owner, repository)) && (t.milestoneId isNotNull) }
      .groupBy { t => t.milestoneId ~ t.closed }
      .map     { case (t1, t2) => (t1._1 ~ t1._2) -> t2.length }
      .toMap

    getMilestones(owner, repository).map { milestone =>
      (milestone, counts.getOrElse((milestone.milestoneId, false), 0), counts.getOrElse((milestone.milestoneId, true), 0))
    }
  }

  def getMilestones(owner: String, repository: String): List[Milestone] =
    Query(Milestones).filter(_.byRepository(owner, repository)).sortBy(_.milestoneId asc).list
}
