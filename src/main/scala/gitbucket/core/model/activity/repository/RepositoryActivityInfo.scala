package gitbucket.core.model.activity.repository

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.model.activity.BaseActivityInfo

sealed trait RepositoryActivityInfo extends BaseActivityInfo {
  def userName: String
  def repositoryName: String
  def activityUserName: String
  def maybeOldUserName: Option[String]
  def maybeOldRepositoryName: Option[String]
}

final case class CreateRepositoryInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String
) extends RepositoryActivityInfo {

  override def maybeOldUserName: Option[String] = None

  override def maybeOldRepositoryName: Option[String] = None

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
) extends RepositoryActivityInfo {

  override def maybeOldUserName: Option[String] = None

  override def maybeOldRepositoryName: Option[String] = None

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
) extends RepositoryActivityInfo {

  override def maybeOldUserName: Option[String] = Some(oldUserName)

  override def maybeOldRepositoryName: Option[String] = None

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
) extends RepositoryActivityInfo {

  override def maybeOldUserName: Option[String] = None

  override def maybeOldRepositoryName: Option[String] = Some(oldRepositoryName)

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
