package gitbucket.core.model.activity.workflow

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

sealed trait ForkActivityInfo extends BaseActivityInfo {
  def userName: String
  def repositoryName: String
  def activityUserName: String
  def forkedUserName: String
}

final case class ForkInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  forkedUserName: String
) extends ForkActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "fork",
      s"[user:$activityUserName] forked [repo:$userName/$repositoryName] to [repo:$forkedUserName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}
