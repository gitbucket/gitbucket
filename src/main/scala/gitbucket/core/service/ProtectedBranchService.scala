package gitbucket.core.service

import gitbucket.core.model._
import gitbucket.core.model.Profile._
import gitbucket.core.plugin.ReceiveHook
import profile.simple._

import org.eclipse.jgit.transport.{ReceivePack, ReceiveCommand}


trait ProtectedBranchService {
  import ProtectedBranchService._
  private def getProtectedBranchInfoOpt(owner: String, repository: String, branch: String)(implicit session: Session): Option[ProtectedBranchInfo] =
    ProtectedBranches
      .leftJoin(ProtectedBranchContexts)
      .on{ case (pb, c) => pb.byBranch(c.userName, c.repositoryName, c.branch) }
      .map{ case (pb, c) => pb -> c.context.? }
      .filter(_._1.byPrimaryKey(owner, repository, branch))
      .list
      .groupBy(_._1)
      .map(p => p._1 -> p._2.flatMap(_._2))
      .map{ case (t1, contexts) =>
        new ProtectedBranchInfo(t1.userName, t1.repositoryName, true, contexts, t1.statusCheckAdmin)
      }.headOption

  def getProtectedBranchInfo(owner: String, repository: String, branch: String)(implicit session: Session): ProtectedBranchInfo =
    getProtectedBranchInfoOpt(owner, repository, branch).getOrElse(ProtectedBranchInfo.disabled(owner, repository))

  def getProtectedBranchList(owner: String, repository: String)(implicit session: Session): List[String] =
    ProtectedBranches.filter(_.byRepository(owner, repository)).map(_.branch).list

  def enableBranchProtection(owner: String, repository: String, branch:String, includeAdministrators: Boolean, contexts: Seq[String])
                            (implicit session: Session): Unit = {
    disableBranchProtection(owner, repository, branch)
    ProtectedBranches.insert(new ProtectedBranch(owner, repository, branch, includeAdministrators && contexts.nonEmpty))
    contexts.map{ context =>
      ProtectedBranchContexts.insert(new ProtectedBranchContext(owner, repository, branch, context))
    }
  }

  def disableBranchProtection(owner: String, repository: String, branch:String)(implicit session: Session): Unit =
    ProtectedBranches.filter(_.byPrimaryKey(owner, repository, branch)).delete

}

object ProtectedBranchService {

  class ProtectedBranchReceiveHook extends ReceiveHook with ProtectedBranchService {
    override def preReceive(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)
                           (implicit session: Session): Option[String] = {
      val branch = command.getRefName.stripPrefix("refs/heads/")
      if(branch != command.getRefName){
        getProtectedBranchInfo(owner, repository, branch).getStopReason(receivePack.isAllowNonFastForwards, command, pusher)
      } else {
        None
      }
    }
  }


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

    def isAdministrator(pusher: String)(implicit session: Session): Boolean =
      pusher == owner || getGroupMembers(owner).filter(gm => gm.userName == pusher && gm.isManager).nonEmpty

    /**
     * Can't be force pushed
     * Can't be deleted
     * Can't have changes merged into them until required status checks pass
     */
    def getStopReason(isAllowNonFastForwards: Boolean, command: ReceiveCommand, pusher: String)(implicit session: Session): Option[String] = {
      if(enabled){
        command.getType() match {
          case ReceiveCommand.Type.UPDATE_NONFASTFORWARD if isAllowNonFastForwards =>
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
      } else {
        None
      }
    }
    def unSuccessedContexts(sha1: String)(implicit session: Session): Set[String] = if(contexts.isEmpty){
      Set.empty
    } else {
      contexts.toSet -- getCommitStatues(owner, repository, sha1).filter(_.state == CommitState.SUCCESS).map(_.context).toSet
    }
    def needStatusCheck(pusher: String)(implicit session: Session): Boolean = pusher match {
      case _ if !enabled              => false
      case _ if contexts.isEmpty      => false
      case _ if includeAdministrators => true
      case p if isAdministrator(p)    => false
      case _                          => true
    }
  }
  object ProtectedBranchInfo{
    def disabled(owner: String, repository: String): ProtectedBranchInfo = ProtectedBranchInfo(owner, repository, false, Nil, false)
  }
}
