package gitbucket.core.model.activity.workflow

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

sealed trait MergeActivityInfo extends BaseActivityInfo {
  def userName: String
  def repositoryName: String
  def activityUserName: String
  def issueId: Int
  def message: String
}

final case class MergeInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  message: String
) extends MergeActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "merge_pullreq",
      s"[user:$activityUserName] merged pull request [pullreq:$userName/$repositoryName#$issueId]",
      Some(message),
      currentDate,
      UUID.randomUUID().toString
    )
}
