package gitbucket.core.model.activity.wiki

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

sealed trait WikiActivityInfo extends BaseActivityInfo {
  def userName: String
  def repositoryName: String
  def activityUserName: String
  def pageName: String
  def maybeCommitId: Option[String]
}

final case class CreateWikiPageInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  pageName: String
) extends WikiActivityInfo {

  override def maybeCommitId: Option[String] = None

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "create_wiki",
      s"[user:$activityUserName] created the [repo:$userName/$repositoryName] wiki",
      Some(pageName),
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class EditWikiPageInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  pageName: String,
  commitId: String
) extends WikiActivityInfo {

  override def maybeCommitId: Option[String] = Some(commitId)

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "edit_wiki",
      s"[user:$activityUserName] edited the [repo:$userName/$repositoryName] wiki",
      Some(s"$pageName:$commitId"),
      currentDate,
      UUID.randomUUID().toString
    )
}
