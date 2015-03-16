package gitbucket.core.service

import gitbucket.core.model.CommitComment
import gitbucket.core.util.{StringUtil, Implicits}

import scala.slick.jdbc.{StaticQuery => Q}
import Q.interpolation
import gitbucket.core.model.Profile._
import profile.simple._
import Implicits._
import StringUtil._


trait CommitsService {

  def getCommitComments(owner: String, repository: String, commitId: String, pullRequest: Boolean)(implicit s: Session) =
    CommitComments filter {
      t => t.byCommit(owner, repository, commitId) && (t.pullRequest === pullRequest || pullRequest)
    } list

  def getCommitComment(owner: String, repository: String, commentId: String)(implicit s: Session) =
    if (commentId forall (_.isDigit))
      CommitComments filter { t =>
        t.byPrimaryKey(commentId.toInt) && t.byRepository(owner, repository)
      } firstOption
    else
      None

  def createCommitComment(owner: String, repository: String, commitId: String, loginUser: String,
    content: String, fileName: Option[String], oldLine: Option[Int], newLine: Option[Int], pullRequest: Boolean)(implicit s: Session): Int =
    CommitComments.autoInc insert CommitComment(
      userName          = owner,
      repositoryName    = repository,
      commitId          = commitId,
      commentedUserName = loginUser,
      content           = content,
      fileName          = fileName,
      oldLine           = oldLine,
      newLine           = newLine,
      registeredDate    = currentDate,
      updatedDate       = currentDate,
      pullRequest       = pullRequest)

  def updateCommitComment(commentId: Int, content: String)(implicit s: Session) =
    CommitComments
      .filter (_.byPrimaryKey(commentId))
      .map { t =>
      t.content -> t.updatedDate
    }.update (content, currentDate)

  def deleteCommitComment(commentId: Int)(implicit s: Session) =
    CommitComments filter (_.byPrimaryKey(commentId)) delete
}
