package gitbucket.core.service

import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile.dateColumnType
import gitbucket.core.model.{CommitState, CommitStatus, Account}

trait CommitStatusService {

  /** insert or update */
  def createCommitStatus(
    userName: String,
    repositoryName: String,
    sha: String,
    context: String,
    state: CommitState,
    targetUrl: Option[String],
    description: Option[String],
    now: java.util.Date,
    creator: Account
  )(implicit s: Session): Int =
    CommitStatuses
      .filter(t => t.byCommit(userName, repositoryName, sha) && t.context === context.bind)
      .map(_.commitStatusId)
      .firstOption match {
      case Some(id: Int) => {
        CommitStatuses
          .filter(_.byPrimaryKey(id))
          .map { t =>
            (t.state, t.targetUrl, t.updatedDate, t.creator, t.description)
          }
          .update((state, targetUrl, now, creator.userName, description))
        id
      }
      case None =>
        (CommitStatuses returning CommitStatuses.map(_.commitStatusId)) insert CommitStatus(
          userName = userName,
          repositoryName = repositoryName,
          commitId = sha,
          context = context,
          state = state,
          targetUrl = targetUrl,
          description = description,
          creator = creator.userName,
          registeredDate = now,
          updatedDate = now
        )
    }

  def getCommitStatusWithSummary(userName: String, repositoryName: String, sha: String)(
    implicit s: Session
  ): Option[(CommitState, List[CommitStatus])] = {
    val statuses = getCommitStatuses(userName, repositoryName, sha)
    if (statuses.isEmpty) {
      None
    } else {
      val summary = CommitState.combine(statuses.groupBy(_.state).keySet)
      Some((summary, statuses))
    }
  }

  def getCommitStatus(userName: String, repositoryName: String, id: Int)(implicit s: Session): Option[CommitStatus] =
    CommitStatuses.filter(t => t.byPrimaryKey(id) && t.byRepository(userName, repositoryName)).firstOption

  def getCommitStatus(userName: String, repositoryName: String, sha: String, context: String)(
    implicit s: Session
  ): Option[CommitStatus] =
    CommitStatuses.filter(t => t.byCommit(userName, repositoryName, sha) && t.context === context.bind).firstOption

  def getCommitStatuses(userName: String, repositoryName: String, sha: String)(
    implicit s: Session
  ): List[CommitStatus] =
    byCommitStatus(userName, repositoryName, sha).list

  def getRecentStatusContexts(userName: String, repositoryName: String, time: java.util.Date)(
    implicit s: Session
  ): List[String] =
    CommitStatuses
      .filter(t => t.byRepository(userName, repositoryName))
      .filter(t => t.updatedDate > time.bind)
      .groupBy(_.context)
      .map(_._1)
      .list

  def getCommitStatusesWithCreator(userName: String, repositoryName: String, sha: String)(
    implicit s: Session
  ): List[(CommitStatus, Account)] =
    byCommitStatus(userName, repositoryName, sha)
      .join(Accounts)
      .filter { case (t, a) => t.creator === a.userName }
      .list

  protected def byCommitStatus(userName: String, repositoryName: String, sha: String)(implicit s: Session) =
    CommitStatuses.filter(t => t.byCommit(userName, repositoryName, sha)).sortBy(_.updatedDate desc)

}
