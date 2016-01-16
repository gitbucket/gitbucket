package gitbucket.core.service

import gitbucket.core.model.Account
import gitbucket.core.util.LockUtil
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.ControlUtil._

import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.errors.NoMergeBaseException
import org.eclipse.jgit.lib.{ObjectId, CommitBuilder, PersonIdent, Repository}
import org.eclipse.jgit.revwalk.RevWalk

trait MergeService {
  import MergeService._
  /**
   * Checks whether conflict will be caused in merging within pull request.
   * Returns true if conflict will be caused.
   */
  def checkConflict(userName: String, repositoryName: String, branch: String, issueId: Int): Boolean = {
    using(Git.open(getRepositoryDir(userName, repositoryName))) { git =>
      MergeCacheInfo(git, branch, issueId).checkConflict()
    }
  }
  /**
   * Checks whether conflict will be caused in merging within pull request.
   * only cache check.
   * Returns Some(true) if conflict will be caused.
   * Returns None if cache has not created yet.
   */
  def checkConflictCache(userName: String, repositoryName: String, branch: String, issueId: Int): Option[Boolean] = {
    using(Git.open(getRepositoryDir(userName, repositoryName))) { git =>
      MergeCacheInfo(git, branch, issueId).checkConflictCache()
    }
  }
  /** merge pull request */
  def mergePullRequest(git:Git, branch: String, issueId: Int, message:String, committer:PersonIdent): Unit = {
    MergeCacheInfo(git, branch, issueId).merge(message, committer)
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
                      remoteUserName: String, remoteRepositoryName: String, remoteBranch: String): Option[(ObjectId, ObjectId, ObjectId)] = {
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
            Some((merger.getResultTreeId, mergeBaseTip, mergeTip))
          }else{
            None
          }
        } catch {
          case e: NoMergeBaseException =>  None
        }
      } finally {
        val refUpdate = git.getRepository.updateRef(refSpec.getDestination)
        refUpdate.setForceUpdate(true)
        refUpdate.delete()
      }
    }
  }
  /**
   * Checks whether conflict will be caused in merging. Returns true if conflict will be caused.
   */
  def checkConflict(userName: String, repositoryName: String, branch: String,
                            requestUserName: String, requestRepositoryName: String, requestBranch: String): Boolean =
    tryMergeRemote(userName, repositoryName, branch, requestUserName, requestRepositoryName, requestBranch).isEmpty

  def pullRemote(localUserName: String, localRepositoryName: String, localBranch: String,
                      remoteUserName: String, remoteRepositoryName: String, remoteBranch: String,
                      loginAccount: Account, message: String): Option[ObjectId] = {
    tryMergeRemote(localUserName, localRepositoryName, localBranch, remoteUserName, remoteRepositoryName, remoteBranch).map{ case (newTreeId, oldBaseId, oldHeadId) =>
      using(Git.open(getRepositoryDir(localUserName, localRepositoryName))) { git =>
        val committer = new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
        val newCommit = Util.createMergeCommit(git.getRepository, newTreeId, committer, message, Seq(oldBaseId, oldHeadId))
        Util.updateRefs(git.getRepository, s"refs/heads/${localBranch}", newCommit, false, committer, Some("merge"))
      }
      oldBaseId
    }
  }

}
object MergeService{
  object Util{
    // return treeId
    def createMergeCommit(repository: Repository, treeId: ObjectId, committer: PersonIdent, message: String, parents: Seq[ObjectId]): ObjectId = {
      val mergeCommit = new CommitBuilder()
      mergeCommit.setTreeId(treeId)
      mergeCommit.setParentIds(parents:_*)
      mergeCommit.setAuthor(committer)
      mergeCommit.setCommitter(committer)
      mergeCommit.setMessage(message)
      // insertObject and got mergeCommit Object Id
      val inserter = repository.newObjectInserter
      val mergeCommitId = inserter.insert(mergeCommit)
      inserter.flush()
      inserter.close()
      mergeCommitId
    }
    def updateRefs(repository: Repository, ref: String, newObjectId: ObjectId, force: Boolean, committer: PersonIdent, refLogMessage: Option[String] = None):Unit = {
      // update refs
      val refUpdate = repository.updateRef(ref)
      refUpdate.setNewObjectId(newObjectId)
      refUpdate.setForceUpdate(force)
      refUpdate.setRefLogIdent(committer)
      refLogMessage.map(refUpdate.setRefLogMessage(_, true))
      refUpdate.update()
    }
  }
  case class MergeCacheInfo(git:Git, branch:String, issueId:Int){
    val repository = git.getRepository
    val mergedBranchName = s"refs/pull/${issueId}/merge"
    val conflictedBranchName = s"refs/pull/${issueId}/conflict"
    lazy val mergeBaseTip = repository.resolve(s"refs/heads/${branch}")
    lazy val mergeTip = repository.resolve(s"refs/pull/${issueId}/head")
    def checkConflictCache(): Option[Boolean] = {
      Option(repository.resolve(mergedBranchName)).flatMap{ merged =>
          if(parseCommit( merged ).getParents().toSet == Set( mergeBaseTip, mergeTip )){
            // merged branch exists
            Some(false)
          }else{
            None
          }
      }.orElse(Option(repository.resolve(conflictedBranchName)).flatMap{ conflicted =>
        if(parseCommit( conflicted ).getParents().toSet == Set( mergeBaseTip, mergeTip )){
          // conflict branch exists
          Some(true)
        }else{
          None
        }
      })
    }
    def checkConflict():Boolean ={
      checkConflictCache.getOrElse(checkConflictForce)
    }
    def checkConflictForce():Boolean ={
      val merger = MergeStrategy.RECURSIVE.newMerger(repository, true)
      val conflicted = try {
        !merger.merge(mergeBaseTip, mergeTip)
      } catch {
        case e: NoMergeBaseException => true
      }
      val mergeTipCommit = using(new RevWalk( repository ))(_.parseCommit( mergeTip ))
      val committer = mergeTipCommit.getCommitterIdent;
      def updateBranch(treeId:ObjectId, message:String, branchName:String){
        // creates merge commit
        val mergeCommitId = createMergeCommit(treeId, committer, message)
        Util.updateRefs(repository, branchName, mergeCommitId, true, committer)
      }
      if(!conflicted){
        updateBranch(merger.getResultTreeId, s"Merge ${mergeTip.name} into ${mergeBaseTip.name}", mergedBranchName)
        git.branchDelete().setForce(true).setBranchNames(conflictedBranchName).call()
      }else{
        updateBranch(mergeTipCommit.getTree().getId(), s"can't merge ${mergeTip.name} into ${mergeBaseTip.name}", conflictedBranchName)
        git.branchDelete().setForce(true).setBranchNames(mergedBranchName).call()
      }
      conflicted
    }
    // update branch from cache
    def merge(message:String, committer:PersonIdent) = {
      if(checkConflict()){
        throw new RuntimeException("This pull request can't merge automatically.")
      }
      val mergeResultCommit = parseCommit( Option(repository.resolve(mergedBranchName)).getOrElse(throw new RuntimeException(s"not found branch ${mergedBranchName}")) )
      // creates merge commit
      val mergeCommitId = createMergeCommit(mergeResultCommit.getTree().getId(), committer, message)
      // update refs
      Util.updateRefs(repository, s"refs/heads/${branch}", mergeCommitId, false, committer, Some("merged"))
    }
    // return treeId
    private def createMergeCommit(treeId: ObjectId, committer: PersonIdent, message: String) =
      Util.createMergeCommit(repository, treeId, committer, message, Seq[ObjectId](mergeBaseTip, mergeTip))

    private def parseCommit(id:ObjectId) = using(new RevWalk( repository ))(_.parseCommit(id))
  }
}