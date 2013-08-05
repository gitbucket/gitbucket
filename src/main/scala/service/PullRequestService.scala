package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._

trait PullRequestService { self: IssuesService =>

  def getPullRequest(owner: String, repository: String, issueId: Int): Option[(Issue, PullRequest)] = {
    val issue = getIssue(owner, repository, issueId.toString)
    if(issue.isDefined){
      Query(PullRequests).filter(_.byPrimaryKey(owner, repository, issueId)).firstOption match {
        case Some(pullreq) => Some((issue.get, pullreq))
        case None          => None
      }
    } else None
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
}