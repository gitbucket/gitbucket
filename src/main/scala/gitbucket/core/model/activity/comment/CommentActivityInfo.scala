package gitbucket.core.model.activity.comment

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

sealed trait CommentActivityInfo extends BaseActivityInfo {
  def userName: String
  def repositoryName: String
  def activityUserName: String
  def comment: String
  def maybeIssueId: Option[Int]
  def maybeCommitId: Option[String]

  protected final def cut(value: String, length: Int): String =
    if (value.length > length) s"${value.substring(0, length)}..."
    else value
}

final case class IssueCommentInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  comment: String,
  issueId: Int
) extends CommentActivityInfo {

  override def maybeIssueId: Option[Int] = Some(issueId)

  override def maybeCommitId: Option[String] = None

  override def toActivity: Activity =
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
}

final case class PullRequestCommentInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  comment: String,
  issueId: Int
) extends CommentActivityInfo {

  override def maybeIssueId: Option[Int] = Some(issueId)

  override def maybeCommitId: Option[String] = None

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "comment_issue",
      s"[user:$activityUserName] commented on pull request [pullreq:$userName/$repositoryName#$issueId]",
      Some(cut(comment, 200)),
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
) extends CommentActivityInfo {

  override def maybeIssueId: Option[Int] = None

  override def maybeCommitId: Option[String] = Some(commitId)

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "comment_commit",
      s"[user:$activityUserName] commented on commit [commit:$userName/$repositoryName@$commitId]",
      Some(cut(comment, 200)),
      currentDate,
      UUID.randomUUID().toString
    )
}
