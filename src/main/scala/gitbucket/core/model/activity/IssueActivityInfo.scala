package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate

final case class CreateIssueInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  title: String
) extends BaseActivityInfo {

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
) extends BaseActivityInfo {

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
) extends BaseActivityInfo {

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
) extends BaseActivityInfo {

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
) extends BaseActivityInfo {

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
) extends BaseActivityInfo {

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
