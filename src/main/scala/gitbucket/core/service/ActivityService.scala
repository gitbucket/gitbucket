package gitbucket.core.service

import gitbucket.core.model.Activity
import gitbucket.core.util.JGitUtil
import gitbucket.core.model.Profile._
import gitbucket.core.util.Directory._
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

import scala.util.Using
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

import gitbucket.core.controller.Context
import org.apache.commons.io.input.ReversedLinesFileReader

import scala.collection.mutable.ListBuffer

trait ActivityService {
  self: RequestCache =>

  private implicit val formats = Serialization.formats(NoTypeHints)

  private def writeLog(activity: Activity): Unit = {
    Using.resource(new FileOutputStream(ActivityLog, true)) { out =>
      out.write((write(activity) + "\n").getBytes(StandardCharsets.UTF_8))
    }
  }

  def getActivitiesByUser(activityUserName: String, isPublic: Boolean)(implicit context: Context): List[Activity] = {
    if (!ActivityLog.exists()) {
      List.empty
    } else {
      val list = new ListBuffer[Activity]
      Using.resource(new ReversedLinesFileReader(ActivityLog, StandardCharsets.UTF_8)) { reader =>
        var json: String = null
        while (list.length < 50 && { json = reader.readLine(); json } != null) {
          val activity = read[Activity](json)
          if (activity.activityUserName == activityUserName) {
            if (isPublic == false) {
              list += activity
            } else {
              if (!getRepositoryInfoFromCache(activity.userName, activity.repositoryName)
                    .map(_.isPrivate)
                    .getOrElse(true)) {
                list += activity
              }
            }
          }
        }
      }
      list.toList
    }
  }

  def getRecentPublicActivities()(implicit context: Context): List[Activity] = {
    if (!ActivityLog.exists()) {
      List.empty
    } else {
      val list = new ListBuffer[Activity]
      Using.resource(new ReversedLinesFileReader(ActivityLog, StandardCharsets.UTF_8)) { reader =>
        var json: String = null
        while (list.length < 50 && { json = reader.readLine(); json } != null) {
          val activity = read[Activity](json)
          if (!getRepositoryInfoFromCache(activity.userName, activity.repositoryName)
                .map(_.isPrivate)
                .getOrElse(true)) {
            list += activity
          }
        }
      }
      list.toList
    }
  }

  def getRecentActivitiesByOwners(owners: Set[String])(implicit context: Context): List[Activity] = {
    if (!ActivityLog.exists()) {
      List.empty
    } else {
      val list = new ListBuffer[Activity]
      Using.resource(new ReversedLinesFileReader(ActivityLog, StandardCharsets.UTF_8)) { reader =>
        var json: String = null
        while (list.length < 50 && { json = reader.readLine(); json } != null) {
          val activity = read[Activity](json)
          if (owners.contains(activity.userName)) {
            list += activity
          } else if (!getRepositoryInfoFromCache(activity.userName, activity.repositoryName)
                       .map(_.isPrivate)
                       .getOrElse(true)) {
            list += activity
          }
        }
      }
      list.toList
    }
  }

  def recordCreateRepositoryActivity(userName: String, repositoryName: String, activityUserName: String): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "create_repository",
        s"[user:${activityUserName}] created [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordDeleteRepositoryActivity(userName: String, repositoryName: String, activityUserName: String): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "delete_repository",
        s"[user:${activityUserName}] deleted [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordTransferRepositoryActivity(
    userName: String,
    repositoryName: String,
    oldUserName: String,
    activityUserName: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "transfer_repository",
        s"[user:${activityUserName}] transfered [repo:${oldUserName}/${repositoryName}] to [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordRenameRepositoryActivity(
    userName: String,
    repositoryName: String,
    oldRepositoryName: String,
    activityUserName: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "rename_repository",
        s"[user:${activityUserName}] renamed [repo:${userName}/${oldRepositoryName}] at [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordCreateIssueActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    title: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "open_issue",
        s"[user:${activityUserName}] opened issue [issue:${userName}/${repositoryName}#${issueId}]",
        Some(title),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordCloseIssueActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    title: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "close_issue",
        s"[user:${activityUserName}] closed issue [issue:${userName}/${repositoryName}#${issueId}]",
        Some(title),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordClosePullRequestActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    title: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "close_issue",
        s"[user:${activityUserName}] closed pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
        Some(title),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordReopenIssueActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    title: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "reopen_issue",
        s"[user:${activityUserName}] reopened issue [issue:${userName}/${repositoryName}#${issueId}]",
        Some(title),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordReopenPullRequestActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    title: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "reopen_issue",
        s"[user:${activityUserName}] reopened pull request [issue:${userName}/${repositoryName}#${issueId}]",
        Some(title),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordCommentIssueActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    comment: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "comment_issue",
        s"[user:${activityUserName}] commented on issue [issue:${userName}/${repositoryName}#${issueId}]",
        Some(cut(comment, 200)),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordCommentPullRequestActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    comment: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "comment_issue",
        s"[user:${activityUserName}] commented on pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
        Some(cut(comment, 200)),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordCommentCommitActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    commitId: String,
    comment: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "comment_commit",
        s"[user:${activityUserName}] commented on commit [commit:${userName}/${repositoryName}@${commitId}]",
        Some(cut(comment, 200)),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordCreateWikiPageActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    pageName: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "create_wiki",
        s"[user:${activityUserName}] created the [repo:${userName}/${repositoryName}] wiki",
        Some(pageName),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordEditWikiPageActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    pageName: String,
    commitId: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "edit_wiki",
        s"[user:${activityUserName}] edited the [repo:${userName}/${repositoryName}] wiki",
        Some(pageName + ":" + commitId),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordPushActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    branchName: String,
    commits: List[JGitUtil.CommitInfo]
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "push",
        s"[user:${activityUserName}] pushed to [branch:${userName}/${repositoryName}#${branchName}] at [repo:${userName}/${repositoryName}]",
        Some(
          commits
            .take(5)
            .map { commit =>
              commit.id + ":" + commit.shortMessage
            }
            .mkString("\n")
        ),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordCreateTagActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    tagName: String,
    commits: List[JGitUtil.CommitInfo]
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "create_tag",
        s"[user:${activityUserName}] created tag [tag:${userName}/${repositoryName}#${tagName}] at [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordDeleteTagActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    tagName: String,
    commits: List[JGitUtil.CommitInfo]
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "delete_tag",
        s"[user:${activityUserName}] deleted tag ${tagName} at [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordCreateBranchActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    branchName: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "create_branch",
        s"[user:${activityUserName}] created branch [branch:${userName}/${repositoryName}#${branchName}] at [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordDeleteBranchActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    branchName: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "delete_branch",
        s"[user:${activityUserName}] deleted branch ${branchName} at [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordForkActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    forkedUserName: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "fork",
        s"[user:${activityUserName}] forked [repo:${userName}/${repositoryName}] to [repo:${forkedUserName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordPullRequestActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    title: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "open_pullreq",
        s"[user:${activityUserName}] opened pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
        Some(title),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordMergeActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    issueId: Int,
    message: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "merge_pullreq",
        s"[user:${activityUserName}] merged pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
        Some(message),
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  def recordReleaseActivity(
    userName: String,
    repositoryName: String,
    activityUserName: String,
    releaseName: String,
    tagName: String
  ): Unit = {
    writeLog(
      Activity(
        userName,
        repositoryName,
        activityUserName,
        "release",
        s"[user:${activityUserName}] released [release:${userName}/${repositoryName}/${tagName}:${releaseName}] at [repo:${userName}/${repositoryName}]",
        None,
        currentDate,
        UUID.randomUUID().toString
      )
    )
  }

  private def cut(value: String, length: Int): String =
    if (value.length > length) value.substring(0, length) + "..." else value
}
