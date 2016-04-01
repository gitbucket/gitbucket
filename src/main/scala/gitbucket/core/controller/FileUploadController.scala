package gitbucket.core.controller

import gitbucket.core.util._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Directory._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.{FileMode, Constants}
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport, FileItem}
import org.apache.commons.io.{IOUtils, FileUtils}

/**
 * Provides Ajax based file upload functionality.
 *
 * This servlet saves uploaded file.
 */
class FileUploadController extends ScalatraServlet with FileUploadSupport {

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3 * 1024 * 1024)))

  post("/image"){
    execute({ (file, fileId) =>
      FileUtils.writeByteArrayToFile(new java.io.File(getTemporaryDir(session.getId), fileId), file.get)
      session += Keys.Session.Upload(fileId) -> file.name
    }, FileUtil.isImage)
  }

  post("/file/:owner/:repository"){
    execute({ (file, fileId) =>
      FileUtils.writeByteArrayToFile(new java.io.File(
        getAttachedDir(params("owner"), params("repository")),
        fileId + "." + FileUtil.getExtension(file.getName)), file.get)
    }, FileUtil.isUploadableType)
  }

  post("/wiki/:owner/:repository"){
    // TODO security
    execute({ (file, fileId) =>
      val owner      = params("owner")
      val repository = params("repository")
      val fileName   = file.getName
      LockUtil.lock(s"${owner}/${repository}/wiki") {
        using(Git.open(Directory.getWikiRepositoryDir(owner, repository))) { git =>
          val builder  = DirCache.newInCore.builder()
          val inserter = git.getRepository.newObjectInserter()
          val headId   = git.getRepository.resolve(Constants.HEAD + "^{commit}")

          if(headId != null){
            JGitUtil.processTree(git, headId){ (path, tree) =>
              if(path != fileName){
                builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
              }
            }
          }

          val bytes = IOUtils.toByteArray(file.getInputStream)
          builder.add(JGitUtil.createDirCacheEntry(fileName, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, bytes)))
          builder.finish()

          val newHeadId = JGitUtil.createNewCommit(git, inserter, headId, builder.getDirCache.writeTree(inserter),
            Constants.HEAD, "committer.fullName", "committer.mailAddress", s"Uploaded ${fileName}") // TODO committer

          fileName
        }
      }
    }, FileUtil.isImage)
  }

  private def execute(f: (FileItem, String) => Unit, mimeTypeChcker: (String) => Boolean) = fileParams.get("file") match {
    case Some(file) if(mimeTypeChcker(file.name)) =>
      defining(FileUtil.generateFileId){ fileId =>
        f(file, fileId)

        Ok(fileId)
      }
    case _ => BadRequest
  }

}
