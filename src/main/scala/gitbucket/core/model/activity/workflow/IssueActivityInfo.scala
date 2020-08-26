package gitbucket.core.model.activity.workflow

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

/**
 * A case of unfortunate naming; GitHub issues and pull requests are
 * technically "issues", since they're given a number, e.g. #1234, etc.
 *
 * It also works well here because most actions belonging to this type
 * share the same information.
 */
sealed trait IssueActivityInfo extends BaseActivityInfo {
  def userName: String
  def repositoryName: String
  def activityUserName: String
  def issueId: Int
  def title: String
}

final case class CreateIssueInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  title: String
) extends IssueActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "open_issue",
      s"[user:$activityUserName] opened issue [issue:$userName/$repositoryName#$issueId]",
      Some(title),
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class CloseIssueInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  title: String
) extends IssueActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "close_issue",
      s"[user:$activityUserName] closed issue [issue:$userName/$repositoryName#$issueId]",
      Some(title),
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class ReopenIssueInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  title: String
) extends IssueActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "reopen_issue",
      s"[user:$activityUserName] reopened issue [issue:$userName/$repositoryName#$issueId]",
      Some(title),
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class OpenPullRequestInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  title: String
) extends IssueActivityInfo {

  override def toActivity: Activity =
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
}

final case class ClosePullRequestInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  title: String
) extends IssueActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "close_issue",
      s"[user:$activityUserName] closed pull request [pullreq:$userName/$repositoryName#$issueId]",
      Some(title),
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class ReopenPullRequestInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  title: String
) extends IssueActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "reopen_issue",
      s"[user:$activityUserName] reopened pull request [issue:$userName/$repositoryName#$issueId]",
      Some(title),
      currentDate,
      UUID.randomUUID().toString
    )
}
