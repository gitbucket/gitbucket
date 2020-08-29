package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate

final case class CreateRepositoryInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "create_repository",
      s"[user:$activityUserName] created [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class DeleteRepositoryInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "delete_repository",
      s"[user:$activityUserName] deleted [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class TransferRepositoryInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  oldUserName: String
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "transfer_repository",
      s"[user:$activityUserName] transferred [repo:$oldUserName/$repositoryName] to [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class RenameRepositoryInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  oldRepositoryName: String
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "rename_repository",
      s"[user:$activityUserName] renamed [repo:$userName/$oldRepositoryName] at [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}
