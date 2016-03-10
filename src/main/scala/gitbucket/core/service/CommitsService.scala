package gitbucket.core.service

import gitbucket.core.model.CommitComment
import gitbucket.core.model.Profile._
import profile.simple._

import gitbucket.core.servlet.Database._
import io.getquill._


trait CommitsService {

  def getCommitComments(owner: String, repository: String, commitId: String, includePullRequest: Boolean) =
    db.run(quote { (owner: String, repository: String, commitId: String, includePullRequest: Boolean) =>
      query[CommitComment].filter { t =>
        t.userName == owner && t.repositoryName == repository && t.commitId == commitId && (t.issueId.isEmpty || includePullRequest)
      }
    })(owner, repository, commitId, includePullRequest)

  def getCommitComment(owner: String, repository: String, commentId: String) =
    if (commentId forall (_.isDigit))
      db.run(quote { (owner: String, repository: String, commentId: Int) =>
        query[CommitComment].filter(t => t.userName == owner && t.repositoryName == repository && t.commentId == commentId)
      })(owner, repository, commentId.toInt).headOption
    else
      None

  def createCommitComment(owner: String, repository: String, commitId: String, loginUser: String,
                          content: String, fileName: Option[String], oldLine: Option[Int], newLine: Option[Int],
                          issueId: Option[Int])(implicit s: Session): Int =
    CommitComments.autoInc insert CommitComment( // TODO Remain Slick code
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

  def updateCommitComment(commentId: Int, content: String) =
    db.run(quote { (commentId: Int, content: String, updatedDate: java.util.Date) =>
      query[CommitComment].filter(_.commentId == commentId).update(_.content -> content, _.updatedDate -> updatedDate)
    })(commentId, content, currentDate)

  def deleteCommitComment(commentId: Int) =
    db.run(quote { (commentId: Int) => query[CommitComment].filter(_.commentId == commentId).delete })(commentId)

}
