package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._

trait PullRequestService { self: IssuesService =>
  import PullRequestService._
//  implicit val getPullRequestCount = GetResult(r => PullRequestCount(r.<<, r.<<))

  def getPullRequest(owner: String, repository: String, issueId: Int): Option[(Issue, PullRequest)] = {
    val issue = getIssue(owner, repository, issueId.toString)
    if(issue.isDefined){
      Query(PullRequests).filter(_.byPrimaryKey(owner, repository, issueId)).firstOption match {
        case Some(pullreq) => Some((issue.get, pullreq))
        case None          => None
      }
    } else None
  }

  def getPullRequestCount(closed: Boolean, repository: Option[(String, String)]): List[PullRequestCount] = {
    Query(PullRequests)
      .innerJoin(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t2.closed         is closed.bind) &&
        (t1.userName       is repository.get._1, repository.isDefined) &&
        (t1.repositoryName is repository.get._2, repository.isDefined)
      }
      .groupBy { case (t1, t2) =>
        t2.openedUserName
      }
      .map { case (userName, t) =>
        userName ~ t.length
      }
      .list
      .map { x =>
        PullRequestCount(x._1, x._2)
      }
      .sortBy(_.count).reverse
  }

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

}

object PullRequestService {

  val PullRequestLimit = 25

  case class PullRequestCount(userName: String, count: Int)

}