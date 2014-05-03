package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import model._

trait PullRequestService { self: IssuesService =>
  import PullRequestService._

  def getPullRequest(owner: String, repository: String, issueId: Int): Option[(Issue, PullRequest)] =
    getIssue(owner, repository, issueId.toString).flatMap{ issue =>
      Query(PullRequests).filter(_.byPrimaryKey(owner, repository, issueId)).firstOption.map{
        pullreq => (issue, pullreq)
      }
    }

  def updateCommitId(owner: String, repository: String, issueId: Int, commitIdTo: String, commitIdFrom: String): Unit =
    Query(PullRequests).filter(_.byPrimaryKey(owner, repository, issueId))
                       .map(pr => pr.commitIdTo ~ pr.commitIdFrom)
                       .update((commitIdTo, commitIdFrom))

  def getPullRequestCountGroupByUser(closed: Boolean, owner: String, repository: Option[String]): List[PullRequestCount] =
    Query(PullRequests)
      .innerJoin(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t2.closed         is closed.bind) &&
        (t1.userName       is owner.bind) &&
        (t1.repositoryName is repository.get.bind, repository.isDefined)
      }
      .groupBy { case (t1, t2) => t2.openedUserName }
      .map { case (userName, t) => userName ~ t.length }
      .sortBy(_._2 desc)
      .list
      .map { x => PullRequestCount(x._1, x._2) }

  def createPullRequest(originUserName: String, originRepositoryName: String, issueId: Int,
        originBranch: String, requestUserName: String, requestRepositoryName: String, requestBranch: String,
        commitIdFrom: String, commitIdTo: String): Unit =
    PullRequests insert (PullRequest(
      originUserName,
      originRepositoryName,
      issueId,
      originBranch,
      requestUserName,
      requestRepositoryName,
      requestBranch,
      commitIdFrom,
      commitIdTo))

  def getPullRequestsByRequest(userName: String, repositoryName: String, branch: String, closed: Boolean): List[PullRequest] =
    Query(PullRequests)
      .innerJoin(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t1.requestUserName       is userName.bind) &&
        (t1.requestRepositoryName is repositoryName.bind) &&
        (t1.requestBranch         is branch.bind) &&
        (t2.closed                is closed.bind)
      }
      .map { case (t1, t2) => t1 }
      .list

}

object PullRequestService {

  val PullRequestLimit = 25

  case class PullRequestCount(userName: String, count: Int)

}
