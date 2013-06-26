package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._
import Milestones._

trait MilestonesService {

  def createMilestone(owner: String, repository: String,
                      title: String, description: Option[String], dueDate: Option[java.util.Date]) =
    Milestones.autoInc insert (owner, repository, title, description, dueDate, None)

  def updateMilestone(milestone: Milestone): Unit =
    Query(Milestones)
      .filter { m =>
        (m.userName       is milestone.userName.bind) &&
        (m.repositoryName is milestone.repositoryName.bind) &&
        (m.milestoneId    is milestone.milestoneId.bind)
      }
      .map { m =>
        m.title ~ m.description.? ~ m.dueDate.? ~ m.closedDate.?
      }
      .update (
        milestone.title,
        milestone.description,
        milestone.dueDate,
        milestone.closedDate)

  def openMilestone(milestone: Milestone): Unit = updateMilestone(milestone.copy(closedDate = None))

  def closeMilestone(milestone: Milestone): Unit = updateMilestone(milestone.copy(closedDate = Some(currentDate)))

  def deleteMilestone(owner: String, repository: String, milestoneId: Int): Unit = {
    Query(Issues)
      .filter { t =>
        (t.userName       is owner.bind) &&
        (t.repositoryName is repository.bind) &&
        (t.milestoneId    is milestoneId.bind)
      }
      .map(_.milestoneId.?)
      .update(None)

    Query(Milestones)
      .filter { t =>
        (t.userName       is owner.bind) &&
        (t.repositoryName is repository.bind) &&
        (t.milestoneId    is milestoneId.bind)
      }
      .delete
  }

  def getMilestone(owner: String, repository: String, milestoneId: Int): Option[Milestone] =
    Query(Milestones)
      .filter { m =>
        (m.userName       is owner.bind) &&
        (m.repositoryName is repository.bind) &&
        (m.milestoneId    is milestoneId.bind)
      }
      .sortBy(_.milestoneId desc)
      .firstOption

  def getMilestoneIssueCounts(owner: String, repository: String): Map[(Int, Boolean), Int] =
    Issues
      .filter { t =>
        (t.userName       is owner.bind) &&
        (t.repositoryName is repository.bind) &&
        (t.milestoneId    isNotNull)
      }
      .groupBy { t =>
        t.milestoneId ~ t.closed
      }
      .map { case (t1, t2) =>
        t1._1 ~ t1._2 ~ t2.length
      }
      .list
      .map { case (milestoneId, closed, count) =>
        (milestoneId, closed) -> count
      }
      .toMap

  def getMilestones(owner: String, repository: String): List[Milestone] =
    Query(Milestones)
      .filter { t =>
        (t.userName is owner.bind) && (t.repositoryName is repository.bind)
      }
      .sortBy(_.milestoneId desc)
      .list
}
