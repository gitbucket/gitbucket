package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import scala.slick.jdbc.{StaticQuery => Q}
import Q.interpolation

import model._
import Milestones._

trait MilestonesService {

  def createMilestone(owner: String, repository: String,
                      title: String, description: Option[String], dueDate: Option[java.util.Date]) =
    Milestones.autoInc insert (owner, repository, title, description, dueDate, None)

  def updateMilestone(milestone: Milestone): Unit =
    Query(Milestones)
      .filter { m => (m.userName is milestone.userName.bind) && (m.repositoryName is milestone.repositoryName.bind) && (m.milestoneId is milestone.milestoneId.bind)}
      .map    { m => m.title ~ m.description.? ~ m.dueDate.? ~ m.closedDate.? }
      .update (
      milestone.title,
      milestone.description,
      milestone.dueDate,
      milestone.closedDate)

  def openMilestone(milestone: Milestone): Unit = updateMilestone(milestone.copy(closedDate = None))

  def closeMilestone(milestone: Milestone): Unit = updateMilestone(milestone.copy(closedDate = Some(currentDate)))

  def deleteMilestone(owner: String, repository: String, milestoneId: Int): Unit = {
    Query(Issues)
      .filter { i => (i.userName is owner.bind) && (i.repositoryName is repository.bind) && (i.milestoneId is milestoneId.bind)}
      .map    { i => i.milestoneId.? }
      .update(None)

    Query(Milestones)
      .filter { i => (i.userName is owner.bind) && (i.repositoryName is repository.bind) && (i.milestoneId is milestoneId.bind)}
      .delete
  }

  def getMilestone(owner: String, repository: String, milestoneId: Int): Option[Milestone] =
    Query(Milestones)
      .filter(m => (m.userName is owner.bind) && (m.repositoryName is repository.bind) && (m.milestoneId is milestoneId.bind))
      .sortBy(_.milestoneId desc)
      .firstOption

  def getMilestoneIssueCounts(owner: String, repository: String): Map[(Int, Boolean), Int] =
    sql"""
      select MILESTONE_ID, CLOSED, COUNT(ISSUE_ID)
       from ISSUE
       where USER_NAME = $owner AND REPOSITORY_NAME = $repository AND MILESTONE_ID IS NOT NULL
       group by USER_NAME, REPOSITORY_NAME, MILESTONE_ID, CLOSED"""
      .as[(Int, Boolean, Int)]
      .list
      .map { case (milestoneId, closed, count) => (milestoneId, closed) -> count }
      .toMap

  def getMilestones(owner: String, repository: String): List[Milestone] =
    Query(Milestones)
      .filter(m => (m.userName is owner.bind) && (m.repositoryName is repository.bind))
      .sortBy(_.milestoneId desc)
      .list
}
