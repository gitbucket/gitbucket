package gitbucket.core.service

import java.util.Date
import gitbucket.core.controller.Context
import gitbucket.core.model.Account
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util._
import gitbucket.core.util.ControlUtil._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.lib._
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.diff.{DiffEntry, DiffFormatter}
import java.io.ByteArrayInputStream
import org.eclipse.jgit.patch._
import org.eclipse.jgit.api.errors.PatchFormatException
import scala.collection.JavaConverters._
import org.eclipse.jgit.dircache.DirCache
import gitbucket.core.util.Directory._


trait CommitFileService {
  
def commitFile(context: Context, repository: RepositoryService.RepositoryInfo,
                          fileName: String, fileBytes: Array[Byte], branch: String, message: String) = {

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
          headName, loginAccount.userName, loginAccount.mailAddress, message)

        inserter.flush()
        inserter.close()

      }
    }
  }

  

}
