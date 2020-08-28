package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate

final case class MergeInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  issueId: Int,
  message: String
) extends BaseActivityInfo {

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
