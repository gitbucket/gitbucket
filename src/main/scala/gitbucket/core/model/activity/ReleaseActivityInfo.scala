package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate

final case class ReleaseInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  releaseName: String,
  tagName: String
) extends BaseActivityInfo {

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
