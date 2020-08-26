package gitbucket.core.model.activity.tag

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

sealed trait TagActivityInfo extends BaseActivityInfo {
  def userName: String
  def repositoryName: String
  def activityUserName: String
}

final case class CreateTagInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  tagName: String,
) extends TagActivityInfo {

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
) extends TagActivityInfo {

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
