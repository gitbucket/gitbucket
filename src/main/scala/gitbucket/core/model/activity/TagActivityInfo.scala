package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate

final case class CreateTagInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  tagName: String,
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "create_tag",
      s"[user:$activityUserName] created tag [tag:$userName/$repositoryName#$tagName] at [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class DeleteTagInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  tagName: String
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "delete_tag",
      s"[user:$activityUserName] deleted tag $tagName at [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}
