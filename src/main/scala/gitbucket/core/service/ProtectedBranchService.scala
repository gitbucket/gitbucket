package gitbucket.core.service

import gitbucket.core.plugin.ReceiveHook
import gitbucket.core.model.Profile.*
import gitbucket.core.model.Profile.profile.blockingApi.*
import gitbucket.core.model.{CommitState, ProtectedBranch, ProtectedBranchContext, ProtectedBranchRestriction, Role}
import gitbucket.core.util.SyntaxSugars.*
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}

trait ProtectedBranchService {
  import ProtectedBranchService._
  private def getProtectedBranchInfoOpt(owner: String, repository: String, branch: String)(implicit
    session: Session
  ): Option[ProtectedBranchInfo] =
    ProtectedBranches
      .joinLeft(ProtectedBranchContexts)
      .on { case pb ~ c => pb.byBranch(c.userName, c.repositoryName, c.branch) }
      .joinLeft(ProtectedBranchRestrictions)
      .on { case pb ~ c ~ r => pb.byBranch(r.userName, r.repositoryName, r.branch) }
      .map { case pb ~ c ~ r => pb -> (c.map(_.context), r.map(_.allowedUser)) }
      .filter(_._1.byPrimaryKey(owner, repository, branch))
      .list
      .groupBy(_._1)
      .headOption
      .map { (p: (ProtectedBranch, List[(ProtectedBranch, (Option[String], Option[String]))])) =>
        p._1 -> (p._2.flatMap(_._2._1), p._2.flatMap(_._2._2))
      }
      .map { case (t1, (contexts, users)) =>
        new ProtectedBranchInfo(
          t1.userName,
          t1.repositoryName,
          t1.branch,
          true,
          if (t1.requiredStatusCheck) Some(contexts) else None,
          t1.enforceAdmins,
          if (t1.restrictions) Some(users) else None
        )
      }

  def getProtectedBranchInfo(owner: String, repository: String, branch: String)(implicit
    session: Session
  ): ProtectedBranchInfo =
    getProtectedBranchInfoOpt(owner, repository, branch).getOrElse(
      ProtectedBranchInfo.disabled(owner, repository, branch)
    )

  def getProtectedBranchList(owner: String, repository: String)(implicit session: Session): List[String] =
    ProtectedBranches.filter(_.byRepository(owner, repository)).map(_.branch).list

  def enableBranchProtection(
    owner: String,
    repository: String,
    branch: String,
    enforceAdmins: Boolean,
    requiredStatusCheck: Boolean,
    contexts: Seq[String],
    restrictions: Boolean,
    restrictionsUsers: Seq[String]
  )(implicit session: Session): Unit = {
    disableBranchProtection(owner, repository, branch)
    ProtectedBranches.insert(
      ProtectedBranch(owner, repository, branch, enforceAdmins, requiredStatusCheck, restrictions)
    )

    if (restrictions) {
      restrictionsUsers.foreach { user =>
        ProtectedBranchRestrictions.insert(ProtectedBranchRestriction(owner, repository, branch, user))
      }
    }

    if (requiredStatusCheck) {
      contexts.foreach { context =>
        ProtectedBranchContexts.insert(ProtectedBranchContext(owner, repository, branch, context))
      }
    }
  }

  def disableBranchProtection(owner: String, repository: String, branch: String)(implicit session: Session): Unit =
    ProtectedBranches.filter(_.byPrimaryKey(owner, repository, branch)).delete
}

object ProtectedBranchService {

  class ProtectedBranchReceiveHook
      extends ReceiveHook
      with ProtectedBranchService
      with RepositoryService
      with AccountService {
    override def preReceive(
      owner: String,
      repository: String,
      receivePack: ReceivePack,
      command: ReceiveCommand,
      pusher: String,
      mergePullRequest: Boolean
    )(implicit session: Session): Option[String] = {
      if (mergePullRequest) {
        None
      } else {
        checkBranchProtection(owner, repository, receivePack, command, pusher)
      }
    }

    private def checkBranchProtection(
      owner: String,
      repository: String,
      receivePack: ReceivePack,
      command: ReceiveCommand,
      pusher: String,
    )(implicit session: Session): Option[String] = {
      val branch = command.getRefName.stripPrefix("refs/heads/")
      if (branch != command.getRefName) {
        val repositoryInfo = getRepository(owner, repository)
        if (
          command.getType == ReceiveCommand.Type.DELETE && repositoryInfo.exists(
            _.repository.defaultBranch == branch
          )
        ) {
          Some(s"refusing to delete the branch: ${command.getRefName}.")
        } else {
          getProtectedBranchInfo(owner, repository, branch).getStopReason(
            receivePack.isAllowNonFastForwards,
            command,
            pusher
          )
        }
      } else {
        println("-> else")
        None
      }
    }
  }

  case class ProtectedBranchInfo(
    owner: String,
    repository: String,
    branch: String,
    enabled: Boolean,
    /**
     * Require status checks to pass before merging
     * Choose which status checks must pass before branches can be merged into test.
     * When enabled, commits must first be pushed to another branch,
     * then merged or pushed directly to test after status checks have passed.
     */
    contexts: Option[Seq[String]],
    /**
     * Include administrators
     * Enforce required status checks for repository administrators.
     */
    enforceAdmins: Boolean,
    /**
     * Users who can push to the branch.
     */
    restrictionsUsers: Option[Seq[String]]
  ) extends AccountService
      with RepositoryService
      with CommitStatusService {

    def isAdministrator(pusher: String)(implicit session: Session): Boolean =
      pusher == owner || getGroupMembers(owner).exists(gm => gm.userName == pusher && gm.isManager) ||
        getCollaborators(owner, repository).exists { case (collaborator, isGroup) =>
          if (collaborator.role == Role.ADMIN.name) {
            if (isGroup) {
              getGroupMembers(collaborator.collaboratorName).exists(gm => gm.userName == pusher)
            } else {
              collaborator.collaboratorName == pusher
            }
          } else false
        }

    /**
     * Can't be force pushed
     * Can't be deleted
     * Can't have changes merged into them until required status checks pass
     */
    def getStopReason(isAllowNonFastForwards: Boolean, command: ReceiveCommand, pusher: String)(implicit
      session: Session
    ): Option[String] = {
      if (enabled) {
        command.getType match {
          case ReceiveCommand.Type.UPDATE_NONFASTFORWARD if isAllowNonFastForwards =>
            Some("Cannot force-push to a protected branch")
          case ReceiveCommand.Type.UPDATE | ReceiveCommand.Type.UPDATE_NONFASTFORWARD if !isPushAllowed(pusher) =>
            Some("You do not have permission to push to this branch")
          case ReceiveCommand.Type.UPDATE | ReceiveCommand.Type.UPDATE_NONFASTFORWARD if needStatusCheck(pusher) =>
            unSuccessedContexts(command.getNewId.name) match {
              case s if s.sizeIs == 1 => Some(s"""Required status check "${s.head}" is expected""")
              case s if s.sizeIs >= 1 =>
                Some(s"${s.size} of ${contexts.map(_.size).getOrElse(0)} required status checks are expected")
              case _ => None
            }
          case ReceiveCommand.Type.DELETE =>
            Some("You do not have permission to push to this branch")
          case _ => None
        }
      } else {
        None
      }
    }

    def unSuccessedContexts(sha1: String)(implicit session: Session): Set[String] = {
      contexts match {
        case None                 => Set.empty
        case Some(x) if x.isEmpty => Set.empty
        case Some(x)              =>
          x.toSet -- getCommitStatuses(owner, repository, sha1)
            .filter(_.state == CommitState.SUCCESS)
            .map(_.context)
            .toSet
      }
    }

    def needStatusCheck(pusher: String)(implicit session: Session): Boolean = pusher match {
      case _ if !enabled           => false
      case _ if contexts.isEmpty   => false
      case _ if enforceAdmins      => true
      case p if isAdministrator(p) => false
      case _                       => true
    }

    def isPushAllowed(pusher: String)(implicit session: Session): Boolean = pusher match {
      case _ if !enabled || restrictionsUsers.isEmpty  => true
      case _ if restrictionsUsers.get.contains(pusher) => true
      case p if isAdministrator(p) && enforceAdmins    => false
      case _                                           => false
    }
  }

  object ProtectedBranchInfo {
    def disabled(owner: String, repository: String, branch: String): ProtectedBranchInfo = {
      ProtectedBranchInfo(
        owner,
        repository,
        branch,
        enabled = false,
        contexts = None,
        enforceAdmins = false,
        restrictionsUsers = None
      )
    }
  }
}
