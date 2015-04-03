package gitbucket.core.util

import gitbucket.core.model._
import gitbucket.core.util.Directory._
import gitbucket.core.util.ControlUtil._

import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.merge._
import org.eclipse.jgit.errors._

import java.nio.file._
import java.util.Date
import java.io.File

object GitSpecUtil {
  def withTestFolder[U](f: File => U) {
    val folder = new File(System.getProperty("java.io.tmpdir"), "test-" + System.nanoTime)
    if(!folder.mkdirs()){
      throw new java.io.IOException("can't create folder "+folder.getAbsolutePath)
    }
    try {
      f(folder)
    } finally {
      FileUtils.deleteQuietly(folder)
    }
  }
  def withTestRepository[U](f: Git => U) = withTestFolder(folder => using(Git.open(createTestRepository(folder)))(f))
  def createTestRepository(dir: File): File = {
    RepositoryCache.clear()
    FileUtils.deleteQuietly(dir)
    Files.createDirectories(dir.toPath())
    JGitUtil.initRepository(dir)
    dir
  }
  def createFile(git: Git, branch: String, name: String, content: String,
                 autorName: String = "dummy", autorEmail: String = "dummy@example.com",
                 message: String = "test commit") {
    val builder = DirCache.newInCore.builder()
    val inserter = git.getRepository.newObjectInserter()
    val headId = git.getRepository.resolve(branch + "^{commit}")
    if(headId!=null){
      JGitUtil.processTree(git, headId){ (path, tree) =>
        if(name != path){
          builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
        }
      }
    }
    builder.add(JGitUtil.createDirCacheEntry(name, FileMode.REGULAR_FILE,
      inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8"))))
    builder.finish()
    JGitUtil.createNewCommit(git, inserter, headId, builder.getDirCache.writeTree(inserter),
      branch, autorName, autorEmail, message)
    inserter.flush()
    inserter.release()
  }
  def getFile(git: Git, branch: String, path: String) = {
    val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))
    val objectId = using(new TreeWalk(git.getRepository)) { walk =>
      walk.addTree(revCommit.getTree)
      walk.setRecursive(true)
      @scala.annotation.tailrec
      def _getPathObjectId: ObjectId = walk.next match {
        case true if (walk.getPathString == path) => walk.getObjectId(0)
        case true                                 => _getPathObjectId
        case false                                => throw new Exception(s"not found ${branch} / ${path}")
      }
      _getPathObjectId
    }
    JGitUtil.getContentInfo(git, path, objectId)
  }
  def mergeAndCommit(git: Git, into:String, branch:String, message:String = null):Unit = {
    val repository = git.getRepository
    val merger = MergeStrategy.RECURSIVE.newMerger(repository, true)
    val mergeBaseTip = repository.resolve(into)
    val mergeTip = repository.resolve(branch)
    val conflicted = try {
      !merger.merge(mergeBaseTip, mergeTip)
    } catch {
      case e: NoMergeBaseException => true
    }
    if(conflicted){
      throw new RuntimeException("conflict!")
    }
    val mergeTipCommit = using(new RevWalk( repository ))(_.parseCommit( mergeTip ))
    val committer = mergeTipCommit.getCommitterIdent;
    // creates merge commit
    val mergeCommit = new CommitBuilder()
    mergeCommit.setTreeId(merger.getResultTreeId)
    mergeCommit.setParentIds(Array[ObjectId](mergeBaseTip, mergeTip): _*)
    mergeCommit.setAuthor(committer)
    mergeCommit.setCommitter(committer)
    mergeCommit.setMessage(message)
    // insertObject and got mergeCommit Object Id
    val inserter = repository.newObjectInserter
    val mergeCommitId = inserter.insert(mergeCommit)
    inserter.flush()
    inserter.release()
    // update refs
    val refUpdate = repository.updateRef(into)
    refUpdate.setNewObjectId(mergeCommitId)
    refUpdate.setForceUpdate(true)
    refUpdate.setRefLogIdent(committer)
    refUpdate.update()
  }
}
