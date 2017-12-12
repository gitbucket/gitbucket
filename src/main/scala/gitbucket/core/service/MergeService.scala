package gitbucket.core.service

import gitbucket.core.model.Account
import gitbucket.core.util.Directory._
import gitbucket.core.util.SyntaxSugars._
import org.eclipse.jgit.merge.{MergeStrategy, Merger, RecursiveMerger}
import org.eclipse.jgit.api.{Git, MergeResult}
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.errors.NoMergeBaseException
import org.eclipse.jgit.lib.{CommitBuilder, ObjectId, PersonIdent, Repository}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}

import scala.collection.JavaConverters._

trait MergeService {
  import MergeService._

  /**
   * Checks whether conflict will be caused in merging within pull request.
   * Returns true if conflict will be caused.
   */
  def checkConflict(userName: String, repositoryName: String, branch: String, issueId: Int): Option[String] = {
    using(Git.open(getRepositoryDir(userName, repositoryName))) { git =>
      new MergeCacheInfo(git, branch, issueId).checkConflict()
    }
  }

  /**
   * Checks whether conflict will be caused in merging within pull request.
   * only cache check.
   * Returns Some(true) if conflict will be caused.
   * Returns None if cache has not created yet.
   */
  def checkConflictCache(userName: String, repositoryName: String, branch: String, issueId: Int): Option[Option[String]] = {
    using(Git.open(getRepositoryDir(userName, repositoryName))) { git =>
      new MergeCacheInfo(git, branch, issueId).checkConflictCache()
    }
  }

  /** merge the pull request with a merge commit */
  def mergePullRequest(git: Git, branch: String, issueId: Int, message: String, committer: PersonIdent): Unit = {
    new MergeCacheInfo(git, branch, issueId).merge(message, committer)
  }

  /** rebase to the head of the pull request branch */
  def rebasePullRequest(git: Git, branch: String, issueId: Int, commits: Seq[RevCommit], committer: PersonIdent): Unit = {
    new MergeCacheInfo(git, branch, issueId).rebase(committer, commits)
  }

  /** squash commits in the pull request and append it */
  def squashPullRequest(git: Git, branch: String, issueId: Int, message: String, committer: PersonIdent): Unit = {
    new MergeCacheInfo(git, branch, issueId).squash(message, committer)
  }

  /** fetch remote branch to my repository refs/pull/{issueId}/head */
  def fetchAsPullRequest(userName: String, repositoryName: String, requestUserName: String, requestRepositoryName: String, requestBranch:String, issueId:Int){
    using(Git.open(getRepositoryDir(userName, repositoryName))){ git =>
      git.fetch
        .setRemote(getRepositoryDir(requestUserName, requestRepositoryName).toURI.toString)
        .setRefSpecs(new RefSpec(s"refs/heads/${requestBranch}:refs/pull/${issueId}/head"))
        .call
    }
  }

  /**
   * Checks whether conflict will be caused in merging. Returns true if conflict will be caused.
   */
  def tryMergeRemote(localUserName: String, localRepositoryName: String, localBranch: String,
                      remoteUserName: String, remoteRepositoryName: String, remoteBranch: String): Either[String, (ObjectId, ObjectId, ObjectId)] = {
    using(Git.open(getRepositoryDir(localUserName, localRepositoryName))) { git =>
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
          if(merger.merge(mergeBaseTip, mergeTip)){
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
  def checkConflict(userName: String, repositoryName: String, branch: String,
                            requestUserName: String, requestRepositoryName: String, requestBranch: String): Option[String] =
    tryMergeRemote(userName, repositoryName, branch, requestUserName, requestRepositoryName, requestBranch).left.toOption

  def pullRemote(localUserName: String, localRepositoryName: String, localBranch: String,
                      remoteUserName: String, remoteRepositoryName: String, remoteBranch: String,
                      loginAccount: Account, message: String): Option[ObjectId] = {
    tryMergeRemote(localUserName, localRepositoryName, localBranch, remoteUserName, remoteRepositoryName, remoteBranch).map { case (newTreeId, oldBaseId, oldHeadId) =>
      using(Git.open(getRepositoryDir(localUserName, localRepositoryName))) { git =>
        val committer = new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
        val newCommit = Util.createMergeCommit(git.getRepository, newTreeId, committer, message, Seq(oldBaseId, oldHeadId))
        Util.updateRefs(git.getRepository, s"refs/heads/${localBranch}", newCommit, false, committer, Some("merge"))
      }
      oldBaseId
    }.toOption
  }

}

object MergeService{

  object Util{
    // return merge commit id
    def createMergeCommit(repository: Repository, treeId: ObjectId, committer: PersonIdent, message: String, parents: Seq[ObjectId]): ObjectId = {
      val mergeCommit = new CommitBuilder()
      mergeCommit.setTreeId(treeId)
      mergeCommit.setParentIds(parents:_*)
      mergeCommit.setAuthor(committer)
      mergeCommit.setCommitter(committer)
      mergeCommit.setMessage(message)
      // insertObject and got mergeCommit Object Id
      using(repository.newObjectInserter){ inserter =>
        val mergeCommitId = inserter.insert(mergeCommit)
        inserter.flush()
        mergeCommitId
      }
    }

    def updateRefs(repository: Repository, ref: String, newObjectId: ObjectId, force: Boolean, committer: PersonIdent, refLogMessage: Option[String] = None): Unit = {
      val refUpdate = repository.updateRef(ref)
      refUpdate.setNewObjectId(newObjectId)
      refUpdate.setForceUpdate(force)
      refUpdate.setRefLogIdent(committer)
      refLogMessage.map(refUpdate.setRefLogMessage(_, true))
      refUpdate.update()
    }
  }

  class MergeCacheInfo(git: Git, branch: String, issueId: Int){

    private val repository = git.getRepository

    private val mergedBranchName     = s"refs/pull/${issueId}/merge"
    private val conflictedBranchName = s"refs/pull/${issueId}/conflict"

    lazy val mergeBaseTip = repository.resolve(s"refs/heads/${branch}")
    lazy val mergeTip     = repository.resolve(s"refs/pull/${issueId}/head")

    def checkConflictCache(): Option[Option[String]] = {
      Option(repository.resolve(mergedBranchName)).flatMap { merged =>
          if(parseCommit(merged).getParents().toSet == Set( mergeBaseTip, mergeTip )){
            // merged branch exists
            Some(None)
          } else {
            None
          }
      }.orElse(Option(repository.resolve(conflictedBranchName)).flatMap{ conflicted =>
        val commit = parseCommit(conflicted)
        if(commit.getParents().toSet == Set( mergeBaseTip, mergeTip )){
          // conflict branch exists
          Some(Some(commit.getFullMessage))
        } else {
          None
        }
      })
    }

    def checkConflict(): Option[String] ={
      checkConflictCache.getOrElse(checkConflictForce)
    }

    def checkConflictForce(): Option[String] ={
      val merger = MergeStrategy.RECURSIVE.newMerger(repository, true)
      val conflicted = try {
        !merger.merge(mergeBaseTip, mergeTip)
      } catch {
        case e: NoMergeBaseException => true
      }
      val mergeTipCommit = using(new RevWalk( repository ))(_.parseCommit( mergeTip ))
      val committer = mergeTipCommit.getCommitterIdent

      def _updateBranch(treeId: ObjectId, message: String, branchName: String){
        // creates merge commit
        val mergeCommitId = createMergeCommit(treeId, committer, message)
        Util.updateRefs(repository, branchName, mergeCommitId, true, committer)
      }

      if(!conflicted){
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
    def merge(message:String, committer:PersonIdent) = {
      if(checkConflict().isDefined){
        throw new RuntimeException("This pull request can't merge automatically.")
      }
      val mergeResultCommit = parseCommit(Option(repository.resolve(mergedBranchName)).getOrElse {
        throw new RuntimeException(s"not found branch ${mergedBranchName}")
      })
      // creates merge commit
      val mergeCommitId = createMergeCommit(mergeResultCommit.getTree().getId(), committer, message)
      // update refs
      Util.updateRefs(repository, s"refs/heads/${branch}", mergeCommitId, false, committer, Some("merged"))
    }

    def rebase(committer: PersonIdent, commits: Seq[RevCommit]): Unit = {
      if(checkConflict().isDefined){
        throw new RuntimeException("This pull request can't merge automatically.")
      }

      def _cloneCommit(commit: RevCommit, parents: Array[ObjectId]): CommitBuilder = {
        val newCommit = new CommitBuilder()
        newCommit.setTreeId(commit.getTree.getId)
        parents.foreach { parentId =>
          newCommit.addParentId(parentId)
        }
        newCommit.setAuthor(commit.getAuthorIdent)
        newCommit.setCommitter(committer)
        newCommit.setMessage(commit.getFullMessage)
        newCommit
      }

      val mergeBaseTipCommit = using(new RevWalk( repository ))(_.parseCommit( mergeBaseTip ))
      var previousId = mergeBaseTipCommit.getId

      using(repository.newObjectInserter){ inserter =>
        commits.foreach { commit =>
          val nextCommit = _cloneCommit(commit, Array(previousId))
          previousId = inserter.insert(nextCommit)
        }
        inserter.flush()
      }

      Util.updateRefs(repository, s"refs/heads/${branch}", previousId, false, committer, Some("rebased"))
    }

    def squash(message: String, committer: PersonIdent): Unit = {
      if(checkConflict().isDefined){
        throw new RuntimeException("This pull request can't merge automatically.")
      }

      val mergeBaseTipCommit = using(new RevWalk( repository ))(_.parseCommit(mergeBaseTip))
      val mergeBranchHeadCommit = using(new RevWalk( repository ))(_.parseCommit(repository.resolve(mergedBranchName)))

      // Create squash commit
      val mergeCommit = new CommitBuilder()
      mergeCommit.setTreeId(mergeBranchHeadCommit.getTree.getId)
      mergeCommit.setParentId(mergeBaseTipCommit)
      mergeCommit.setAuthor(mergeBranchHeadCommit.getAuthorIdent)
      mergeCommit.setCommitter(committer)
      mergeCommit.setMessage(message)

      // insertObject and got squash commit Object Id
      val newCommitId = using(repository.newObjectInserter){ inserter =>
        val newCommitId = inserter.insert(mergeCommit)
        inserter.flush()
        newCommitId
      }

      Util.updateRefs(repository, mergedBranchName, newCommitId, true, committer)

      // rebase to squash commit
      Util.updateRefs(repository, s"refs/heads/${branch}", repository.resolve(mergedBranchName), false, committer, Some("squashed"))
    }

    // return treeId
    private def createMergeCommit(treeId: ObjectId, committer: PersonIdent, message: String) =
      Util.createMergeCommit(repository, treeId, committer, message, Seq[ObjectId](mergeBaseTip, mergeTip))

    private def parseCommit(id: ObjectId) = using(new RevWalk( repository ))(_.parseCommit(id))

  }

  private def createConflictMessage(mergeTip: ObjectId, mergeBaseTip: ObjectId, merger: Merger): String = {
    val mergeResults = merger.asInstanceOf[RecursiveMerger].getMergeResults

    s"can't merge ${mergeTip.name} into ${mergeBaseTip.name}\n\n" +
      "Conflicting files:\n" +
      mergeResults.asScala.map { case (key, _) => "- " + key + "\n" }.mkString
  }

}
