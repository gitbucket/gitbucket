package gitbucket.core.service

import gitbucket.core.model.{Collaborator, Repository, Account, CommitState}
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
  def getProtectedBranchInfo(owner: String, repository: String, branch: String)(implicit session: Session): Option[ProtectedBranchInfo] = {
    // TODO: mock
    MockDB.data.get((owner, repository, branch)).map{ case (includeAdministrators, requireStatusChecksToPass) =>
      new ProtectedBranchInfo(owner, repository, requireStatusChecksToPass, includeAdministrators)
    }
  }
  def enableBranchProtection(owner: String, repository: String, branch:String, includeAdministrators: Boolean, requireStatusChecksToPass: Seq[String])(implicit session: Session): Unit = {
    // TODO: mock
    MockDB.data.put((owner, repository, branch), includeAdministrators -> requireStatusChecksToPass)
  }
  def disableBranchProtection(owner: String, repository: String, branch:String)(implicit session: Session): Unit = {
    // TODO: mock
    MockDB.data.remove((owner, repository, branch))
  }

  def getBranchProtectedReason(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)(implicit session: Session): Option[String] = {
    val branch = command.getRefName.stripPrefix("refs/heads/")
    if(branch != command.getRefName){
      getProtectedBranchInfo(owner, repository, branch).flatMap(_.getStopReason(receivePack, command, pusher))
    }else{
      None
    }
  }
}
object ProtectedBrancheService {
  case class ProtectedBranchInfo(
    owner: String,
    repository: String,
    /**
     * Require status checks to pass before merging
     * Choose which status checks must pass before branches can be merged into test.
     * When enabled, commits must first be pushed to another branch,
     * then merged or pushed directly to test after status checks have passed.
     */
    requireStatusChecksToPass: Seq[String],
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
      command.getType() match {
        case ReceiveCommand.Type.UPDATE|ReceiveCommand.Type.UPDATE_NONFASTFORWARD if receivePack.isAllowNonFastForwards =>
          Some("Cannot force-push to a protected branch")
        case ReceiveCommand.Type.UPDATE|ReceiveCommand.Type.UPDATE_NONFASTFORWARD if needStatusCheck(pusher) =>
          unSuccessedContexts(command.getNewId.name) match {
            case s if s.size == 1 => Some(s"""Required status check "${s.toSeq(0)}" is expected""")
            case s if s.size >= 1 => Some(s"${s.size} of ${requireStatusChecksToPass.size} required status checks are expected")
            case _ => None
          }
        case ReceiveCommand.Type.DELETE =>
          Some("Cannot delete a protected branch")
        case _ => None
      }
    }
    def unSuccessedContexts(sha1: String)(implicit session: Session): Set[String] = if(requireStatusChecksToPass.isEmpty){
      Set.empty
    } else {
      requireStatusChecksToPass.toSet -- getCommitStatues(owner, repository, sha1).filter(_.state == CommitState.SUCCESS).map(_.context).toSet
    }
    def needStatusCheck(pusher: String)(implicit session: Session): Boolean =
      if(requireStatusChecksToPass.isEmpty){
        false
      }else if(includeAdministrators){
        true
      }else{
        !isAdministrator(pusher)
      }
  }
}
