package gitbucket.core.model

import java.util.UUID

import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

final case class CreateWikiActivityActionInfo(
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

final case class EditWikiActivityActionInfo(
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

final case class DeleteWikiActivityActionInfo(
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
      "delete_wiki",
      s"[user:$activityUserName] deleted the [$pageName] page in [repo:$userName/$repositoryName] wiki",
      Some(s"$pageName:$commitId"),
      currentDate,
      UUID.randomUUID().toString
    )
}
