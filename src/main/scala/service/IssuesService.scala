package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import scala.slick.jdbc.{StaticQuery => Q}
import Q.interpolation

import model._

trait IssuesService {
  def getIssue(owner: String, repository: String, issueId: String) =
    if (issueId forall (_.isDigit))
      Query(Issues) filter { t =>
        (t.userName is owner.bind) &&
        (t.repositoryName is repository.bind) &&
        (t.issueId is issueId.toInt.bind)
      } firstOption
    else None

  def searchIssue(owner: String, repository: String,
      // TODO It is better to have a DTO
      closed: Boolean) =
    Query(Issues) filter { t =>
      (t.userName is owner.bind) &&
      (t.repositoryName is repository.bind) &&
      (t.closed is closed.bind)
    } list

  def saveIssue(owner: String, repository: String, loginUser: String,
      title: String, content: Option[String]) =
    // next id number
    sql"SELECT ISSUE_ID + 1 FROM ISSUE_ID WHERE USER_NAME = $owner AND REPOSITORY_NAME = $repository FOR UPDATE".as[Int]
        .firstOption.filter { id =>
      Issues insert Issue(
          owner,
          repository,
          id,
          loginUser,
          None,
          None,
          title,
          content,
          false,
          new java.sql.Date(System.currentTimeMillis),	// TODO
          new java.sql.Date(System.currentTimeMillis))

      // increment issue id
      IssueId.filter { t =>
        (t.userName is owner.bind) && (t.repositoryName is repository.bind)
      }.map(_.issueId).update(id) > 0
    } get

  def createMilestone(owner: String, repository: String,
      title: String, description: Option[String], dueDate: Option[java.sql.Date]): Unit = {
    Milestones.ins insert (owner, repository, title, description, dueDate, None)
  }

  def updateMilestone(milestone: Milestone): Unit =
    Query(Milestones)
      .filter { m => (m.userName is milestone.userName.bind) && (m.repositoryName is milestone.repositoryName.bind) && (m.milestoneId is milestone.milestoneId.bind)}
      .map    { m => m.title ~ m.description.? ~ m.dueDate.? ~ m.closedDate.? }
      .update (
      milestone.title,
      milestone.description,
      milestone.dueDate,
      milestone.closedDate)

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

  def getMilestones(owner: String, repository: String): List[Milestone] =
    Query(Milestones)
      .filter(m => (m.userName is owner.bind) && (m.repositoryName is repository.bind))
      .sortBy(_.milestoneId desc)
      .list

}