package gitbucket.core.service

import gitbucket.core.model.Profile._
import profile.simple._

import gitbucket.core.model.{CommitState, CommitStatus, Account}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.StringUtil._
import gitbucket.core.service.RepositoryService.RepositoryInfo


trait CommitStatusService {
  /** insert or update */
  def createCommitStatus(userName: String, repositoryName: String, sha:String, context:String, state:CommitState, targetUrl:Option[String], description:Option[String], now:java.util.Date, creator:Account)(implicit s: Session): Int =
    CommitStatuses.filter(t => t.byCommit(userName, repositoryName, sha) && t.context===context.bind )
      .map(_.commitStatusId).firstOption match {
    case Some(id:Int) => {
      CommitStatuses.filter(_.byPrimaryKey(id)).map{
        t => (t.state , t.targetUrl , t.updatedDate , t.creator, t.description)
        }.update( (state, targetUrl, now, creator.userName, description) )
        id
      }
    case None => (CommitStatuses returning CommitStatuses.map(_.commitStatusId)) += CommitStatus(
        userName       = userName,
        repositoryName = repositoryName,
        commitId       = sha,
        context        = context,
        state          = state,
        targetUrl      = targetUrl,
        description    = description,
        creator        = creator.userName,
        registeredDate = now,
        updatedDate    = now)
      }

  def getCommitStatus(userName: String, repositoryName: String, id: Int)(implicit s: Session) :Option[CommitStatus] =
    CommitStatuses.filter(t => t.byPrimaryKey(id) && t.byRepository(userName, repositoryName)).firstOption

  def getCommitStatus(userName: String, repositoryName: String, sha: String, context: String)(implicit s: Session) :Option[CommitStatus] =
    CommitStatuses.filter(t => t.byCommit(userName, repositoryName, sha) && t.context===context.bind ).firstOption

  def getCommitStatues(userName: String, repositoryName: String, sha: String)(implicit s: Session) :List[CommitStatus] =
    byCommitStatues(userName, repositoryName, sha).list

  def getCommitStatuesWithCreator(userName: String, repositoryName: String, sha: String)(implicit s: Session) :List[(CommitStatus, Account)] =
    byCommitStatues(userName, repositoryName, sha).innerJoin(Accounts)
      .filter{ case (t,a) => t.creator === a.userName }.list

  protected def byCommitStatues(userName: String, repositoryName: String, sha: String)(implicit s: Session) = 
    CommitStatuses.filter(t => t.byCommit(userName, repositoryName, sha) ).sortBy(_.updatedDate desc)

}