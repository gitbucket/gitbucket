package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate

final case class CreateWikiPageInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  pageName: String
) extends BaseActivityInfo {

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
) extends BaseActivityInfo {

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

final case class DeleteWikiInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  pageName: String,
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "delete_wiki",
      s"[user:$activityUserName] deleted the page [$pageName] in the [repo:$userName/$repositoryName] wiki",
      additionalInfo = None,
      currentDate,
      UUID.randomUUID().toString
    )
}
