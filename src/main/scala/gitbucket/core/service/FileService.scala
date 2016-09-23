package gitbucket.core.service


import gitbucket.core.api._
import gitbucket.core.controller.Context
import gitbucket.core.model.Account
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Directory._
import java.io.ByteArrayInputStream
import java.util.Date
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.lib._
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.diff.{DiffEntry, DiffFormatter}
import org.eclipse.jgit.patch._
import org.eclipse.jgit.api.errors.PatchFormatException
import org.eclipse.jgit.dircache.DirCache
import scala.collection.JavaConverters._
 
trait FileService {
 
 def getFileContent(git: Git, branch: String, path: String) = {
 
    val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))
    val objectId = using(new TreeWalk(git.getRepository)) { walk =>
      walk.addTree(revCommit.getTree)
      walk.setRecursive(true)
      @scala.annotation.tailrec
      def _getPathObjectId: ObjectId = walk.next match {
        case true if (walk.getPathString == path) => walk.getObjectId(0)
        case true                                 => _getPathObjectId
        case false                                =>  null //throw new Exception(s"not found ${branch} / ${path}")
      }
      _getPathObjectId
    }

    if (objectId != null)
      { JGitUtil.getContentInfo(git, path, objectId) }
    else
      { org.scalatra.NotFound(ApiError(s"File not found: ${branch}/${path}")) }
  }
 
 
def commitFile(context: Context, repository: RepositoryService.RepositoryInfo, branch: String,
                          fileName: String, fileBytes: Array[Byte], commitMessage: String) = {

    LockUtil.lock(s"${repository.owner}/${repository.name}") {
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>

        val loginAccount = context.loginAccount.get
        val builder = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headName = s"refs/heads/${branch}"
        val headTip = git.getRepository.resolve(headName)
        
        if(headTip != null){
          JGitUtil.processTree(git, headTip) { (path, tree) =>
            if(path != fileName){
                builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
            } else {
              // do nothing
            }
          }
        }

        builder.add(JGitUtil.createDirCacheEntry(fileName,
          org.eclipse.jgit.lib.FileMode.REGULAR_FILE, inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, fileBytes)))
        builder.finish()

        val commitId = JGitUtil.createNewCommit(git, inserter, headTip, builder.getDirCache.writeTree(inserter),
          headName, loginAccount.userName, loginAccount.mailAddress, commitMessage)

        inserter.flush()
        inserter.close()

        val _commitInfo  = new JGitUtil.CommitInfo(JGitUtil.getRevCommitFromId(git, commitId))
        _commitInfo
      }
    }
  }


}
