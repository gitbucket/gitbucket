package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate

final case class IssueCommentInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  comment: String,
  issueId: Int
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "comment_issue",
      s"[user:${activityUserName}] commented on issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(trimInfoString(comment, 200)),
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class PullRequestCommentInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  comment: String,
  issueId: Int
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "comment_issue",
      s"[user:$activityUserName] commented on pull request [pullreq:$userName/$repositoryName#$issueId]",
      Some(trimInfoString(comment, 200)),
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class CommitCommentInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  comment: String,
  commitId: String
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "comment_commit",
      s"[user:$activityUserName] commented on commit [commit:$userName/$repositoryName@$commitId]",
      Some(trimInfoString(comment, 200)),
      currentDate,
      UUID.randomUUID().toString
    )
}
