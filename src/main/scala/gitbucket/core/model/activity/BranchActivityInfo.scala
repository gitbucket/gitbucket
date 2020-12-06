package gitbucket.core.model.activity

import java.util.UUID

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile.currentDate
import gitbucket.core.util.JGitUtil.CommitInfo

final case class PushInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  branchName: String,
  commits: List[CommitInfo]
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "push",
      s"[user:$activityUserName] pushed to [branch:$userName/$repositoryName#$branchName] at [repo:$userName/$repositoryName]",
      Some(buildCommitSummary(commits)),
      currentDate,
      UUID.randomUUID().toString
    )

  private[this] def buildCommitSummary(commits: List[CommitInfo]): String =
    commits
      .take(5)
      .map(commit => s"${commit.id}:${commit.shortMessage}")
      .mkString("\n")
}

final case class CreateBranchInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  branchName: String
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "create_branch",
      s"[user:$activityUserName] created branch [branch:$userName/$repositoryName#$branchName] at [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}

final case class DeleteBranchInfo(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  branchName: String
) extends BaseActivityInfo {

  override def toActivity: Activity =
    Activity(
      userName,
      repositoryName,
      activityUserName,
      "delete_branch",
      s"[user:$activityUserName] deleted branch $branchName at [repo:$userName/$repositoryName]",
      None,
      currentDate,
      UUID.randomUUID().toString
    )
}
