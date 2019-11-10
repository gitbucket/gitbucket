package gitbucket.core.service

import gitbucket.core.api.JsonFormat
import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, PullRequest, WebHook}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.Directory._
import gitbucket.core.util.{JGitUtil, LockUtil}
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.SystemSettingsService.SystemSettings
import org.eclipse.jgit.merge.{MergeStrategy, Merger, RecursiveMerger}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.errors.NoMergeBaseException
import org.eclipse.jgit.lib.{CommitBuilder, ObjectId, PersonIdent, Repository}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}

import scala.jdk.CollectionConverters._
import scala.util.Using

trait MergeService {
  self: AccountService
    with ActivityService
    with IssuesService
    with RepositoryService
    with PullRequestService
    with WebHookPullRequestService =>

  import MergeService._

  /**
   * Checks whether conflict will be caused in merging within pull request.
   * Returns true if conflict will be caused.
   */
  def checkConflict(userName: String, repositoryName: String, branch: String, issueId: Int): Option[String] = {
    Using.resource(Git.open(getRepositoryDir(userName, repositoryName))) { git =>
      new MergeCacheInfo(git, branch, issueId).checkConflict()
    }
  }

  /**
   * Checks whether conflict will be caused in merging within pull request.
   * only cache check.
   * Returns Some(true) if conflict will be caused.
   * Returns None if cache has not created yet.
   */
  def checkConflictCache(
    userName: String,
    repositoryName: String,
    branch: String,
    issueId: Int
  ): Option[Option[String]] = {
    Using.resource(Git.open(getRepositoryDir(userName, repositoryName))) { git =>
      new MergeCacheInfo(git, branch, issueId).checkConflictCache()
    }
  }

  /** merge the pull request with a merge commit */
  def mergePullRequest(
    git: Git,
    branch: String,
    issueId: Int,
    message: String,
    committer: PersonIdent
  ): ObjectId = {
    new MergeCacheInfo(git, branch, issueId).merge(message, committer)
  }

  /** rebase to the head of the pull request branch */
  def rebasePullRequest(
    git: Git,
    branch: String,
    issueId: Int,
    commits: Seq[RevCommit],
    committer: PersonIdent
  ): ObjectId = {
    new MergeCacheInfo(git, branch, issueId).rebase(committer, commits)
  }

  /** squash commits in the pull request and append it */
  def squashPullRequest(
    git: Git,
    branch: String,
    issueId: Int,
    message: String,
    committer: PersonIdent
  ): ObjectId = {
    new MergeCacheInfo(git, branch, issueId).squash(message, committer)
  }

  /** fetch remote branch to my repository refs/pull/{issueId}/head */
  def fetchAsPullRequest(
    userName: String,
    repositoryName: String,
    requestUserName: String,
    requestRepositoryName: String,
    requestBranch: String,
    issueId: Int
  ): Unit = {
    Using.resource(Git.open(getRepositoryDir(userName, repositoryName))) { git =>
      git.fetch
        .setRemote(getRepositoryDir(requestUserName, requestRepositoryName).toURI.toString)
        .setRefSpecs(new RefSpec(s"refs/heads/${requestBranch}:refs/pull/${issueId}/head"))
        .call
    }
  }

  /**
   * Checks whether conflict will be caused in merging. Returns true if conflict will be caused.
   */
  def tryMergeRemote(
    localUserName: String,
    localRepositoryName: String,
    localBranch: String,
    remoteUserName: String,
    remoteRepositoryName: String,
    remoteBranch: String
  ): Either[String, (ObjectId, ObjectId, ObjectId)] = {
    Using.resource(Git.open(getRepositoryDir(localUserName, localRepositoryName))) { git =>
      val remoteRefName = s"refs/heads/${remoteBranch}"
      val tmpRefName = s"refs/remote-temp/${remoteUserName}/${remoteRepositoryName}/${remoteBranch}"
      val refSpec = new RefSpec(s"${remoteRefName}:${tmpRefName}").setForceUpdate(true)
      try {
        // fetch objects from origin repository branch
        git.fetch
          .setRemote(getRepositoryDir(remoteUserName, remoteRepositoryName).toURI.toString)
          .setRefSpecs(refSpec)
          .call
        // merge conflict check
        val merger = MergeStrategy.RECURSIVE.newMerger(git.getRepository, true)
        val mergeBaseTip = git.getRepository.resolve(s"refs/heads/${localBranch}")
        val mergeTip = git.getRepository.resolve(tmpRefName)
        try {
          if (merger.merge(mergeBaseTip, mergeTip)) {
            Right((merger.getResultTreeId, mergeBaseTip, mergeTip))
          } else {
            Left(createConflictMessage(mergeTip, mergeBaseTip, merger))
          }
        } catch {
          case e: NoMergeBaseException => Left(e.toString)
        }
      } finally {
        val refUpdate = git.getRepository.updateRef(refSpec.getDestination)
        refUpdate.setForceUpdate(true)
        refUpdate.delete()
      }
    }
  }

  /**
   * Checks whether conflict will be caused in merging. Returns `Some(errorMessage)` if conflict will be caused.
   */
  def checkConflict(
    userName: String,
    repositoryName: String,
    branch: String,
    requestUserName: String,
    requestRepositoryName: String,
    requestBranch: String
  ): Option[String] =
    tryMergeRemote(userName, repositoryName, branch, requestUserName, requestRepositoryName, requestBranch).left.toOption

  def pullRemote(
    localRepository: RepositoryInfo,
    localBranch: String,
    remoteRepository: RepositoryInfo,
    remoteBranch: String,
    loginAccount: Account,
    message: String,
    pullreq: Option[PullRequest],
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context): Option[ObjectId] = {
    val localUserName = localRepository.owner
    val localRepositoryName = localRepository.name
    val remoteUserName = remoteRepository.owner
    val remoteRepositoryName = remoteRepository.name
    tryMergeRemote(localUserName, localRepositoryName, localBranch, remoteUserName, remoteRepositoryName, remoteBranch).map {
      case (newTreeId, oldBaseId, oldHeadId) =>
        Using.resource(Git.open(getRepositoryDir(localUserName, localRepositoryName))) { git =>
          val existIds = JGitUtil.getAllCommitIds(git).toSet

          val committer = new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
          val newCommit =
            Util.createMergeCommit(git.getRepository, newTreeId, committer, message, Seq(oldBaseId, oldHeadId))
          Util.updateRefs(git.getRepository, s"refs/heads/${localBranch}", newCommit, false, committer, Some("merge"))

          val commits = git.log
            .addRange(oldBaseId, newCommit)
            .call
            .iterator
            .asScala
            .map(c => new JGitUtil.CommitInfo(c))
            .toList

          commits.foreach { commit =>
            if (!existIds.contains(commit.id)) {
              createIssueComment(localUserName, localRepositoryName, commit)
            }
          }

          // record activity
          recordPushActivity(
            localUserName,
            localRepositoryName,
            loginAccount.userName,
            localBranch,
            commits
          )

          // close issue by commit message
          if (localBranch == localRepository.repository.defaultBranch) {
            commits.foreach { commit =>
              closeIssuesFromMessage(commit.fullMessage, loginAccount.userName, localUserName, localRepositoryName)
                .foreach { issueId =>
                  getIssue(localRepository.owner, localRepository.name, issueId.toString).foreach { issue =>
                    callIssuesWebHook("closed", localRepository, issue, loginAccount, settings)
                    PluginRegistry().getIssueHooks
                      .foreach(
                        _.closedByCommitComment(issue, localRepository, commit.fullMessage, loginAccount)
                      )
                  }
                }
            }
          }

          pullreq.foreach { pullreq =>
            callWebHookOf(localRepository.owner, localRepository.name, WebHook.Push, settings) {
              for {
                ownerAccount <- getAccountByUserName(localRepository.owner)
              } yield {
                WebHookService.WebHookPushPayload(
                  git,
                  loginAccount,
                  pullreq.requestBranch,
                  localRepository,
                  commits,
                  ownerAccount,
                  oldId = oldBaseId,
                  newId = newCommit
                )
              }
            }
          }
        }
        oldBaseId
    }.toOption
  }

  def mergePullRequest(
    repository: RepositoryInfo,
    issueId: Int,
    loginAccount: Account,
    message: String,
    strategy: String,
    isDraft: Boolean,
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context, context: Context): Either[String, ObjectId] = {
    if (!isDraft) {
      if (repository.repository.options.mergeOptions.split(",").contains(strategy)) {
        LockUtil.lock(s"${repository.owner}/${repository.name}") {
          getPullRequest(repository.owner, repository.name, issueId)
            .map {
              case (issue, pullreq) =>
                Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
                  // mark issue as merged and close.
                  val commentId =
                    createComment(repository.owner, repository.name, loginAccount.userName, issueId, message, "merge")
                  createComment(repository.owner, repository.name, loginAccount.userName, issueId, "Close", "close")
                  updateClosed(repository.owner, repository.name, issueId, true)

                  // record activity
                  recordMergeActivity(repository.owner, repository.name, loginAccount.userName, issueId, message)

                  val (commits, _) = getRequestCompareInfo(
                    repository.owner,
                    repository.name,
                    pullreq.commitIdFrom,
                    pullreq.requestUserName,
                    pullreq.requestRepositoryName,
                    pullreq.commitIdTo
                  )

                  val revCommits = Using
                    .resource(new RevWalk(git.getRepository)) { revWalk =>
                      commits.flatten.map { commit =>
                        revWalk.parseCommit(git.getRepository.resolve(commit.id))
                      }
                    }
                    .reverse

                  // merge git repository
                  (strategy match {
                    case "merge-commit" =>
                      Some(
                        mergePullRequest(
                          git,
                          pullreq.branch,
                          issueId,
                          s"Merge pull request #${issueId} from ${pullreq.requestUserName}/${pullreq.requestBranch}\n\n" + message,
                          new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
                        )
                      )
                    case "rebase" =>
                      Some(
                        rebasePullRequest(
                          git,
                          pullreq.branch,
                          issueId,
                          revCommits,
                          new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
                        )
                      )
                    case "squash" =>
                      Some(
                        squashPullRequest(
                          git,
                          pullreq.branch,
                          issueId,
                          s"${issue.title} (#${issueId})\n\n" + message,
                          new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
                        )
                      )
                    case _ =>
                      None
                  }) match {
                    case Some(newCommitId) =>
                      // close issue by content of pull request
                      val defaultBranch = getRepository(repository.owner, repository.name).get.repository.defaultBranch
                      if (pullreq.branch == defaultBranch) {
                        commits.flatten.foreach { commit =>
                          closeIssuesFromMessage(
                            commit.fullMessage,
                            loginAccount.userName,
                            repository.owner,
                            repository.name
                          ).foreach { issueId =>
                            getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
                              callIssuesWebHook("closed", repository, issue, loginAccount, context.settings)
                              PluginRegistry().getIssueHooks
                                .foreach(_.closedByCommitComment(issue, repository, commit.fullMessage, loginAccount))
                            }
                          }
                        }
                        val issueContent = issue.title + " " + issue.content.getOrElse("")
                        closeIssuesFromMessage(
                          issueContent,
                          loginAccount.userName,
                          repository.owner,
                          repository.name
                        ).foreach { issueId =>
                          getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
                            callIssuesWebHook("closed", repository, issue, loginAccount, context.settings)
                            PluginRegistry().getIssueHooks
                              .foreach(_.closedByCommitComment(issue, repository, issueContent, loginAccount))
                          }
                        }
                        closeIssuesFromMessage(message, loginAccount.userName, repository.owner, repository.name)
                          .foreach { issueId =>
                            getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
                              callIssuesWebHook("closed", repository, issue, loginAccount, context.settings)
                              PluginRegistry().getIssueHooks
                                .foreach(_.closedByCommitComment(issue, repository, issueContent, loginAccount))
                            }
                          }
                      }

                      callPullRequestWebHook("closed", repository, issueId, context.loginAccount.get, context.settings)

                      updatePullRequests(
                        repository.owner,
                        repository.name,
                        pullreq.branch,
                        loginAccount,
                        "closed",
                        settings
                      )

                      // call hooks
                      PluginRegistry().getPullRequestHooks.foreach { h =>
                        h.addedComment(commentId, message, issue, repository)
                        h.merged(issue, repository)
                      }

                      Right(newCommitId)
                    case None =>
                      Left("Unknown strategy")
                  }
                }
              case _ => Left("Unknown error")
            }
            .getOrElse(Left("Pull request not found"))
        }
      } else Left("Strategy not allowed")
    } else Left("Draft pull requests cannot be merged")
  }
}

object MergeService {

  object Util {
    // return merge commit id
    def createMergeCommit(
      repository: Repository,
      treeId: ObjectId,
      committer: PersonIdent,
      message: String,
      parents: Seq[ObjectId]
    ): ObjectId = {
      val mergeCommit = new CommitBuilder()
      mergeCommit.setTreeId(treeId)
      mergeCommit.setParentIds(parents: _*)
      mergeCommit.setAuthor(committer)
      mergeCommit.setCommitter(committer)
      mergeCommit.setMessage(message)
      // insertObject and got mergeCommit Object Id
      Using.resource(repository.newObjectInserter) { inserter =>
        val mergeCommitId = inserter.insert(mergeCommit)
        inserter.flush()
        mergeCommitId
      }
    }

    def updateRefs(
      repository: Repository,
      ref: String,
      newObjectId: ObjectId,
      force: Boolean,
      committer: PersonIdent,
      refLogMessage: Option[String] = None
    ): ObjectId = {
      val refUpdate = repository.updateRef(ref)
      refUpdate.setNewObjectId(newObjectId)
      refUpdate.setForceUpdate(force)
      refUpdate.setRefLogIdent(committer)
      refLogMessage.foreach(refUpdate.setRefLogMessage(_, true))
      refUpdate.update()

      newObjectId
    }
  }

  class MergeCacheInfo(git: Git, branch: String, issueId: Int) {

    private val repository = git.getRepository

    private val mergedBranchName = s"refs/pull/${issueId}/merge"
    private val conflictedBranchName = s"refs/pull/${issueId}/conflict"

    lazy val mergeBaseTip = repository.resolve(s"refs/heads/${branch}")
    lazy val mergeTip = repository.resolve(s"refs/pull/${issueId}/head")

    def checkConflictCache(): Option[Option[String]] = {
      Option(repository.resolve(mergedBranchName))
        .flatMap { merged =>
          if (parseCommit(merged).getParents().toSet == Set(mergeBaseTip, mergeTip)) {
            // merged branch exists
            Some(None)
          } else {
            None
          }
        }
        .orElse(Option(repository.resolve(conflictedBranchName)).flatMap { conflicted =>
          val commit = parseCommit(conflicted)
          if (commit.getParents().toSet == Set(mergeBaseTip, mergeTip)) {
            // conflict branch exists
            Some(Some(commit.getFullMessage))
          } else {
            None
          }
        })
    }

    def checkConflict(): Option[String] = {
      checkConflictCache.getOrElse(checkConflictForce)
    }

    def checkConflictForce(): Option[String] = {
      val merger = MergeStrategy.RECURSIVE.newMerger(repository, true)
      val conflicted = try {
        !merger.merge(mergeBaseTip, mergeTip)
      } catch {
        case e: NoMergeBaseException => true
      }
      val mergeTipCommit = Using.resource(new RevWalk(repository))(_.parseCommit(mergeTip))
      val committer = mergeTipCommit.getCommitterIdent

      def _updateBranch(treeId: ObjectId, message: String, branchName: String): Unit = {
        // creates merge commit
        val mergeCommitId = createMergeCommit(treeId, committer, message)
        Util.updateRefs(repository, branchName, mergeCommitId, true, committer)
      }

      if (!conflicted) {
        _updateBranch(merger.getResultTreeId, s"Merge ${mergeTip.name} into ${mergeBaseTip.name}", mergedBranchName)
        git.branchDelete().setForce(true).setBranchNames(conflictedBranchName).call()
        None
      } else {
        val message = createConflictMessage(mergeTip, mergeBaseTip, merger)
        _updateBranch(mergeTipCommit.getTree().getId(), message, conflictedBranchName)
        git.branchDelete().setForce(true).setBranchNames(mergedBranchName).call()
        Some(message)
      }
    }

    // update branch from cache
    def merge(message: String, committer: PersonIdent): ObjectId = {
      if (checkConflict().isDefined) {
        throw new RuntimeException("This pull request can't merge automatically.")
      }
      val mergeResultCommit = parseCommit(Option(repository.resolve(mergedBranchName)).getOrElse {
        throw new RuntimeException(s"Not found branch ${mergedBranchName}")
      })
      // creates merge commit
      val mergeCommitId = createMergeCommit(mergeResultCommit.getTree().getId(), committer, message)
      // update refs
      Util.updateRefs(repository, s"refs/heads/${branch}", mergeCommitId, false, committer, Some("merged"))
    }

    def rebase(committer: PersonIdent, commits: Seq[RevCommit]): ObjectId = {
      if (checkConflict().isDefined) {
        throw new RuntimeException("This pull request can't merge automatically.")
      }

      def _cloneCommit(commit: RevCommit, parentId: ObjectId, baseId: ObjectId): CommitBuilder = {
        val merger = MergeStrategy.RECURSIVE.newMerger(repository, true)
        merger.merge(commit.toObjectId, baseId)

        val newCommit = new CommitBuilder()
        newCommit.setTreeId(merger.getResultTreeId)
        newCommit.addParentId(parentId)
        newCommit.setAuthor(commit.getAuthorIdent)
        newCommit.setCommitter(committer)
        newCommit.setMessage(commit.getFullMessage)
        newCommit
      }

      val mergeBaseTipCommit = Using.resource(new RevWalk(repository))(_.parseCommit(mergeBaseTip))
      var previousId = mergeBaseTipCommit.getId

      Using.resource(repository.newObjectInserter) { inserter =>
        commits.foreach { commit =>
          val nextCommit = _cloneCommit(commit, previousId, mergeBaseTipCommit.getId)
          previousId = inserter.insert(nextCommit)
        }
        inserter.flush()
      }

      Util.updateRefs(repository, s"refs/heads/${branch}", previousId, false, committer, Some("rebased"))
    }

    def squash(message: String, committer: PersonIdent): ObjectId = {
      if (checkConflict().isDefined) {
        throw new RuntimeException("This pull request can't merge automatically.")
      }

      val mergeBaseTipCommit = Using.resource(new RevWalk(repository))(_.parseCommit(mergeBaseTip))
      val mergeBranchHeadCommit =
        Using.resource(new RevWalk(repository))(_.parseCommit(repository.resolve(mergedBranchName)))

      // Create squash commit
      val mergeCommit = new CommitBuilder()
      mergeCommit.setTreeId(mergeBranchHeadCommit.getTree.getId)
      mergeCommit.setParentId(mergeBaseTipCommit)
      mergeCommit.setAuthor(mergeBranchHeadCommit.getAuthorIdent)
      mergeCommit.setCommitter(committer)
      mergeCommit.setMessage(message)

      // insertObject and got squash commit Object Id
      val newCommitId = Using.resource(repository.newObjectInserter) { inserter =>
        val newCommitId = inserter.insert(mergeCommit)
        inserter.flush()
        newCommitId
      }

      Util.updateRefs(repository, mergedBranchName, newCommitId, true, committer)

      // rebase to squash commit
      Util.updateRefs(
        repository,
        s"refs/heads/${branch}",
        repository.resolve(mergedBranchName),
        false,
        committer,
        Some("squashed")
      )
    }

    // return treeId
    private def createMergeCommit(treeId: ObjectId, committer: PersonIdent, message: String) =
      Util.createMergeCommit(repository, treeId, committer, message, Seq[ObjectId](mergeBaseTip, mergeTip))

    private def parseCommit(id: ObjectId) = Using.resource(new RevWalk(repository))(_.parseCommit(id))

  }

  private def createConflictMessage(mergeTip: ObjectId, mergeBaseTip: ObjectId, merger: Merger): String = {
    val mergeResults = merger.asInstanceOf[RecursiveMerger].getMergeResults

    s"Can't merge ${mergeTip.name} into ${mergeBaseTip.name}\n\n" +
      "Conflicting files:\n" +
      mergeResults.asScala.map { case (key, _) => "- `" + key + "`\n" }.mkString
  }

}
