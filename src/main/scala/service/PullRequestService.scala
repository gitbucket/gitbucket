package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._

//import scala.slick.jdbc.{StaticQuery => Q}
//import Q.interpolation


trait PullRequestService {

  def createPullRequest(originUserName: String, originRepositoryName: String, issueId: Int,
        originBranch: String, requestUserName: String, requestRepositoryName: String, requestCommitId: String): Unit =
    PullRequests insert (PullRequest(
      originUserName,
      originRepositoryName,
      issueId,
      originBranch,
      requestUserName,
      requestRepositoryName,
      requestCommitId))

}
