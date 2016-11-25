package gitbucket.core.service

import gitbucket.core.model.CommitComment
import gitbucket.core.model.Profile._
import profile._
import profile.blockingApi._
import gitbucket.core.model.Profile.dateColumnType

trait CommitsService {

  def getCommitComments(owner: String, repository: String, commitId: String, includePullRequest: Boolean)(implicit s: Session) =
    CommitComments filter {
      t => t.byCommit(owner, repository, commitId) && (t.issueId.isEmpty || includePullRequest)
    } list

  def getCommitComment(owner: String, repository: String, commentId: String)(implicit s: Session) =
    if (commentId forall (_.isDigit))
      CommitComments filter { t =>
        t.byPrimaryKey(commentId.toInt) && t.byRepository(owner, repository)
      } firstOption
    else
      None

  def createCommitComment(owner: String, repository: String, commitId: String, loginUser: String,
                          content: String, fileName: Option[String], oldLine: Option[Int], newLine: Option[Int],
                          issueId: Option[Int])(implicit s: Session): Int =
    CommitComments returning CommitComments.map(_.commentId) insert CommitComment(
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
      issueId           = issueId)

  def updateCommitComment(commentId: Int, content: String)(implicit s: Session) = {
    CommitComments
      .filter (_.byPrimaryKey(commentId))
      .map { t => (t.content, t.updatedDate) }
      .update (content, currentDate)
  }

  def deleteCommitComment(commentId: Int)(implicit s: Session) =
    CommitComments filter (_.byPrimaryKey(commentId)) delete
}
