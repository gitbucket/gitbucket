package gitbucket.core.service

import gitbucket.core.model.{Collaborator, Repository, Account, CommitState, CommitStatus}
import gitbucket.core.model.Profile._
import gitbucket.core.util.JGitUtil
import profile.simple._

import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.lib.ObjectId


object MockDB{
  val data:scala.collection.mutable.Map[(String,String,String),(Boolean, Seq[String])] = scala.collection.mutable.Map(("root", "test58", "hoge2") -> (false, Seq.empty))
}

trait ProtectedBrancheService {
  import ProtectedBrancheService._
  private def getProtectedBranchInfoOpt(owner: String, repository: String, branch: String)(implicit session: Session): Option[ProtectedBranchInfo] = {
    // TODO: mock
    MockDB.data.get((owner, repository, branch)).map{ case (includeAdministrators, contexts) =>
      new ProtectedBranchInfo(owner, repository, true, contexts, includeAdministrators)
    }
  }
  def getProtectedBranchInfo(owner: String, repository: String, branch: String)(implicit session: Session): ProtectedBranchInfo = {
    getProtectedBranchInfoOpt(owner, repository, branch).getOrElse(ProtectedBranchInfo.disabled(owner, repository))
  }
  def isProtectedBranchNeedStatusCheck(owner: String, repository: String, branch: String, user: String)(implicit session: Session): Boolean =
    getProtectedBranchInfo(owner, repository, branch).needStatusCheck(user)
  def getProtectedBranchList(owner: String, repository: String)(implicit session: Session): List[String] = {
    // TODO: mock
    MockDB.data.filter{
      case ((owner, repository, _), _) => true
      case _ => false
    }.map{ case ((_, _, branch), _) => branch }.toList
  }
  def enableBranchProtection(owner: String, repository: String, branch:String, includeAdministrators: Boolean, contexts: Seq[String])(implicit session: Session): Unit = {
    // TODO: mock
    MockDB.data.put((owner, repository, branch), includeAdministrators -> contexts)
  }
  def disableBranchProtection(owner: String, repository: String, branch:String)(implicit session: Session): Unit = {
    // TODO: mock
    MockDB.data.remove((owner, repository, branch))
  }

  def getBranchProtectedReason(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)(implicit session: Session): Option[String] = {
    val branch = command.getRefName.stripPrefix("refs/heads/")
    if(branch != command.getRefName){
      getProtectedBranchInfo(owner, repository, branch).getStopReason(receivePack, command, pusher)
    }else{
      None
    }
  }
}
object ProtectedBrancheService {
  case class ProtectedBranchInfo(
    owner: String,
    repository: String,
    enabled: Boolean,
    /**
     * Require status checks to pass before merging
     * Choose which status checks must pass before branches can be merged into test.
     * When enabled, commits must first be pushed to another branch,
     * then merged or pushed directly to test after status checks have passed.
     */
    contexts: Seq[String],
    /**
     * Include administrators
     * Enforce required status checks for repository administrators.
     */
    includeAdministrators: Boolean) extends AccountService with CommitStatusService {

    def isAdministrator(pusher: String)(implicit session: Session): Boolean = pusher == owner || getGroupMembers(owner).filter(gm => gm.userName == pusher && gm.isManager).nonEmpty

    /**
     * Can't be force pushed
     * Can't be deleted
     * Can't have changes merged into them until required status checks pass
     */
    def getStopReason(receivePack: ReceivePack, command: ReceiveCommand, pusher: String)(implicit session: Session): Option[String] = {
      if(enabled){
        command.getType() match {
          case ReceiveCommand.Type.UPDATE|ReceiveCommand.Type.UPDATE_NONFASTFORWARD if receivePack.isAllowNonFastForwards =>
            Some("Cannot force-push to a protected branch")
          case ReceiveCommand.Type.UPDATE|ReceiveCommand.Type.UPDATE_NONFASTFORWARD if needStatusCheck(pusher) =>
            unSuccessedContexts(command.getNewId.name) match {
              case s if s.size == 1 => Some(s"""Required status check "${s.toSeq(0)}" is expected""")
              case s if s.size >= 1 => Some(s"${s.size} of ${contexts.size} required status checks are expected")
              case _ => None
            }
          case ReceiveCommand.Type.DELETE =>
            Some("Cannot delete a protected branch")
          case _ => None
        }
      }else{
        None
      }
    }
    def unSuccessedContexts(sha1: String)(implicit session: Session): Set[String] = if(contexts.isEmpty){
      Set.empty
    } else {
      contexts.toSet -- getCommitStatues(owner, repository, sha1).filter(_.state == CommitState.SUCCESS).map(_.context).toSet
    }
    def needStatusCheck(pusher: String)(implicit session: Session): Boolean =
      if(!enabled || contexts.isEmpty){
        false
      }else if(includeAdministrators){
        true
      }else{
        !isAdministrator(pusher)
      }
    def withRequireStatues(statuses: List[CommitStatus]): List[CommitStatus] = {
      statuses ++ (contexts.toSet -- statuses.map(_.context).toSet).map{ context => CommitStatus(
        commitStatusId = 0,
        userName = owner,
        repositoryName = repository,
        commitId = "",
        context = context,
        state = CommitState.PENDING,
        targetUrl = None,
        description = Some("Waiting for status to be reported"),
        creator = "",
        registeredDate = new java.util.Date(),
        updatedDate = new java.util.Date())
      }
    }
    def hasProblem(statuses: List[CommitStatus], sha1: String, account: String)(implicit session: Session): Boolean =
      needStatusCheck(account) && contexts.exists(context => statuses.find(_.context == context).map(_.state) != Some(CommitState.SUCCESS))
  }
  object ProtectedBranchInfo{
    def disabled(owner: String, repository: String): ProtectedBranchInfo = ProtectedBranchInfo(owner, repository, false, Nil, false)
  }
}
