package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate

final case class ForkInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  forkedUserName: String
) extends BaseActivityInfo {

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
