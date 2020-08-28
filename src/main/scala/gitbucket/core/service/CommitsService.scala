package gitbucket.core.service

import java.io.File

import gitbucket.core.api.JsonFormat
import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, CommitComment}
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile.dateColumnType
import gitbucket.core.model.activity.{CommitCommentInfo, PullRequestCommentInfo}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.Directory._
import gitbucket.core.util.{FileUtil, StringUtil}
import org.apache.commons.io.FileUtils

trait CommitsService {
  self: ActivityService with PullRequestService with WebHookPullRequestReviewCommentService =>

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
    repository: RepositoryInfo,
    commitId: String,
    loginAccount: Account,
    content: String,
    fileName: Option[String],
    oldLine: Option[Int],
    newLine: Option[Int],
    diff: Option[String],
    issueId: Option[Int]
  )(implicit s: Session, c: JsonFormat.Context, context: Context): Int = {
    val commentId = CommitComments returning CommitComments.map(_.commentId) insert CommitComment(
      userName = repository.owner,
      repositoryName = repository.name,
      commitId = commitId,
      commentedUserName = loginAccount.userName,
      content = content,
      fileName = fileName,
      oldLine = oldLine,
      newLine = newLine,
      registeredDate = currentDate,
      updatedDate = currentDate,
      issueId = issueId,
      originalCommitId = commitId,
      originalOldLine = oldLine,
      originalNewLine = newLine
    )

    for {
      fileName <- fileName
      diff <- diff
    } {
      saveCommitCommentDiff(
        repository.owner,
        repository.name,
        commitId,
        fileName,
        oldLine,
        newLine,
        diff
      )
    }

    val comment = getCommitComment(repository.owner, repository.name, commentId.toString).get
    issueId match {
      case Some(issueId) =>
        getPullRequest(repository.owner, repository.name, issueId).foreach {
          case (issue, pullRequest) =>
            val pullRequestCommentInfo =
              PullRequestCommentInfo(repository.owner, repository.name, loginAccount.userName, content, issueId)
            recordActivity(pullRequestCommentInfo)
            PluginRegistry().getPullRequestHooks.foreach(_.addedComment(commentId, content, issue, repository))
            callPullRequestReviewCommentWebHook(
              "create",
              comment,
              repository,
              issue,
              pullRequest,
              loginAccount,
              context.settings
            )
        }
      case None =>
        val commitCommentInfo =
          CommitCommentInfo(repository.owner, repository.name, loginAccount.userName, content, commitId)
        recordActivity(commitCommentInfo)
    }

    commentId
  }

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
