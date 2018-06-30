package gitbucket.core.service

import java.io.File

import gitbucket.core.model.CommitComment
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile.dateColumnType
import gitbucket.core.util.Directory._
import gitbucket.core.util.{FileUtil, StringUtil}
import org.apache.commons.io.FileUtils

trait CommitsService {

  def getCommitComments(owner: String, repository: String, commitId: String, includePullRequest: Boolean)(
    implicit s: Session
  ) =
    CommitComments filter { t =>
      t.byCommit(owner, repository, commitId) && (t.issueId.isEmpty || includePullRequest)
    } list

  def getCommitComment(owner: String, repository: String, commentId: String)(implicit s: Session) =
    if (commentId forall (_.isDigit))
      CommitComments filter { t =>
        t.byPrimaryKey(commentId.toInt) && t.byRepository(owner, repository)
      } firstOption
    else
      None

  def createCommitComment(
    owner: String,
    repository: String,
    commitId: String,
    loginUser: String,
    content: String,
    fileName: Option[String],
    oldLine: Option[Int],
    newLine: Option[Int],
    issueId: Option[Int]
  )(implicit s: Session): Int =
    CommitComments returning CommitComments.map(_.commentId) insert CommitComment(
      userName = owner,
      repositoryName = repository,
      commitId = commitId,
      commentedUserName = loginUser,
      content = content,
      fileName = fileName,
      oldLine = oldLine,
      newLine = newLine,
      registeredDate = currentDate,
      updatedDate = currentDate,
      issueId = issueId
    )

  def updateCommitCommentPosition(commentId: Int, commitId: String, oldLine: Option[Int], newLine: Option[Int])(
    implicit s: Session
  ): Unit =
    CommitComments
      .filter(_.byPrimaryKey(commentId))
      .map { t =>
        (t.commitId, t.oldLine, t.newLine)
      }
      .update(commitId, oldLine, newLine)

  def updateCommitComment(commentId: Int, content: String)(implicit s: Session) = {
    CommitComments
      .filter(_.byPrimaryKey(commentId))
      .map { t =>
        (t.content, t.updatedDate)
      }
      .update(content, currentDate)
  }

  def deleteCommitComment(commentId: Int)(implicit s: Session) =
    CommitComments filter (_.byPrimaryKey(commentId)) delete

  def saveCommitCommentDiff(
    owner: String,
    repository: String,
    commitId: String,
    fileName: String,
    oldLine: Option[Int],
    newLine: Option[Int],
    diffJson: String
  ): Unit = {
    val dir = new File(getDiffDir(owner, repository), FileUtil.checkFilename(commitId))
    if (!dir.exists) {
      dir.mkdirs()
    }
    val file = diffFile(dir, fileName, oldLine, newLine)
    FileUtils.write(file, diffJson, "UTF-8")
  }

  def loadCommitCommentDiff(
    owner: String,
    repository: String,
    commitId: String,
    fileName: String,
    oldLine: Option[Int],
    newLine: Option[Int]
  ): Option[String] = {
    val dir = new File(getDiffDir(owner, repository), FileUtil.checkFilename(commitId))
    val file = diffFile(dir, fileName, oldLine, newLine)
    if (file.exists) {
      Option(FileUtils.readFileToString(file, "UTF-8"))
    } else None
  }

  private def diffFile(dir: java.io.File, fileName: String, oldLine: Option[Int], newLine: Option[Int]): File = {
    new File(
      dir,
      StringUtil.sha1(
        fileName +
          "_oldLine:" + oldLine.map(_.toString).getOrElse("") +
          "_newLine:" + newLine.map(_.toString).getOrElse("")
      )
    )
  }

}
