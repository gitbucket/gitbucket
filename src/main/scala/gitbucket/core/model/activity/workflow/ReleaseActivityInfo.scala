package gitbucket.core.model.activity.workflow

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

sealed trait ReleaseActivityInfo extends BaseActivityInfo {
  def userName: String
  def repositoryName: String
  def activityUserName: String
  def releaseName: String
  def tagName: String
}

final case class ReleaseInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  releaseName: String,
  tagName: String
) extends ReleaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "release",
      s"[user:$activityUserName] released [release:$userName/$repositoryName/$tagName:$releaseName] at [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}
