package service

import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import util._
import _root_.util.ControlUtil._
import org.eclipse.jgit.treewalk.{TreeWalk, CanonicalTreeParser}
import org.eclipse.jgit.lib._
import org.eclipse.jgit.dircache.{DirCache, DirCacheEntry}
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.diff.{DiffEntry, DiffFormatter}
import java.io.ByteArrayInputStream
import org.eclipse.jgit.patch._
import org.eclipse.jgit.api.errors.PatchFormatException
import scala.collection.JavaConverters._
import scala.Some
import service.RepositoryService.RepositoryInfo


object WikiService {
  
  /**
   * The model for wiki page.
   * 
   * @param name the page name
   * @param content the page content
   * @param committer the last committer
   * @param time the last modified time
   * @param id the latest commit id
   */
  case class WikiPageInfo(name: String, content: String, committer: String, time: Date, id: String)
  
  /**
   * The model for wiki page history.
   * 
   * @param name the page name
   * @param committer the committer the committer
   * @param message the commit message
   * @param date the commit date
   */
  case class WikiPageHistoryInfo(name: String, committer: String, message: String, date: Date)

  def httpUrl(repository: RepositoryInfo) = repository.httpUrl.replaceFirst("\\.git\\Z", ".wiki.git")

  def sshUrl(repository: RepositoryInfo, settings: SystemSettingsService.SystemSettings, userName: String) =
    repository.sshUrl(settings.sshPort.getOrElse(SystemSettingsService.DefaultSshPort), userName).replaceFirst("\\.git\\Z", ".wiki.git")
}

trait WikiService {
  import WikiService._

  def createWikiRepository(loginAccount: model.Account, owner: String, repository: String): Unit =
    LockUtil.lock(s"${owner}/${repository}/wiki"){
      defining(Directory.getWikiRepositoryDir(owner, repository)){ dir =>
        if(!dir.exists){
          JGitUtil.initRepository(dir)
          saveWikiPage(owner, repository, "Home", "Home", s"Welcome to the ${repository} wiki!!", loginAccount, "Initial Commit", None)
        }
      }
    }

  /**
   * Returns the wiki page.
   */
  def getWikiPage(owner: String, repository: String, pageName: String): Option[WikiPageInfo] = {
    using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
      if(!JGitUtil.isEmpty(git)){
        JGitUtil.getFileList(git, "master", ".").find(_.name == pageName + ".md").map { file =>
          WikiPageInfo(file.name, StringUtil.convertFromByteArray(git.getRepository.open(file.id).getBytes),
                       file.committer, file.time, file.commitId)
        }
      } else None
    }
  }

  /**
   * Returns the content of the specified file.
   */
  def getFileContent(owner: String, repository: String, path: String): Option[Array[Byte]] =
    using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
      if(!JGitUtil.isEmpty(git)){
        val index = path.lastIndexOf('/')
        val parentPath = if(index < 0) "."  else path.substring(0, index)
        val fileName   = if(index < 0) path else path.substring(index + 1)

        JGitUtil.getFileList(git, "master", parentPath).find(_.name == fileName).map { file =>
          git.getRepository.open(file.id).getBytes
        }
      } else None
    }

  /**
   * Returns the list of wiki page names.
   */
  def getWikiPageList(owner: String, repository: String): List[String] = {
    using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
      JGitUtil.getFileList(git, "master", ".")
        .filter(_.name.endsWith(".md"))
        .map(_.name.stripSuffix(".md"))
        .sortBy(x => x)
    }
  }

  /**
   * Reverts specified changes.
   */
  def revertWikiPage(owner: String, repository: String, from: String, to: String,
                     committer: model.Account, pageName: Option[String]): Boolean = {

    case class RevertInfo(operation: String, filePath: String, source: String)

    try {
      LockUtil.lock(s"${owner}/${repository}/wiki"){
        using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>

          val reader = git.getRepository.newObjectReader
          val oldTreeIter = new CanonicalTreeParser
          oldTreeIter.reset(reader, git.getRepository.resolve(from + "^{tree}"))

          val newTreeIter = new CanonicalTreeParser
          newTreeIter.reset(reader, git.getRepository.resolve(to + "^{tree}"))

          val diffs = git.diff.setNewTree(oldTreeIter).setOldTree(newTreeIter).call.asScala.filter { diff =>
            pageName match {
              case Some(x) => diff.getNewPath == x + ".md"
              case None    => true
            }
          }

          val patch = using(new java.io.ByteArrayOutputStream()){ out =>
            val formatter = new DiffFormatter(out)
            formatter.setRepository(git.getRepository)
            formatter.format(diffs.asJava)
            new String(out.toByteArray, "UTF-8")
          }

          val p = new Patch()
          p.parse(new ByteArrayInputStream(patch.getBytes("UTF-8")))
          if(!p.getErrors.isEmpty){
            throw new PatchFormatException(p.getErrors())
          }
          val revertInfo = (p.getFiles.asScala.map { fh =>
            fh.getChangeType match {
              case DiffEntry.ChangeType.MODIFY => {
                val source = getWikiPage(owner, repository, fh.getNewPath.stripSuffix(".md")).map(_.content).getOrElse("")
                val applied = PatchUtil.apply(source, patch, fh)
                if(applied != null){
                  Seq(RevertInfo("ADD", fh.getNewPath, applied))
                } else Nil
              }
              case DiffEntry.ChangeType.ADD => {
                val applied = PatchUtil.apply("", patch, fh)
                if(applied != null){
                  Seq(RevertInfo("ADD", fh.getNewPath, applied))
                } else Nil
              }
              case DiffEntry.ChangeType.DELETE => {
                Seq(RevertInfo("DELETE", fh.getNewPath, ""))
              }
              case DiffEntry.ChangeType.RENAME => {
                val applied = PatchUtil.apply("", patch, fh)
                if(applied != null){
                  Seq(RevertInfo("DELETE", fh.getOldPath, ""), RevertInfo("ADD", fh.getNewPath, applied))
                } else {
                  Seq(RevertInfo("DELETE", fh.getOldPath, ""))
                }
              }
              case _ => Nil
            }
          }).flatten

          if(revertInfo.nonEmpty){
            val builder  = DirCache.newInCore.builder()
            val inserter = git.getRepository.newObjectInserter()
            val headId   = git.getRepository.resolve(Constants.HEAD + "^{commit}")

            JGitUtil.processTree(git, headId){ (path, tree) =>
              if(revertInfo.find(x => x.filePath == path).isEmpty){
                builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
              }
            }

            revertInfo.filter(_.operation == "ADD").foreach { x =>
              builder.add(JGitUtil.createDirCacheEntry(x.filePath, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, x.source.getBytes("UTF-8"))))
            }
            builder.finish()

            JGitUtil.createNewCommit(git, inserter, headId, builder.getDirCache.writeTree(inserter), committer.fullName, committer.mailAddress,
              pageName match {
                case Some(x) => s"Revert ${from} ... ${to} on ${x}"
                case None    => s"Revert ${from} ... ${to}"
              })
          }
        }
      }
      true
    } catch {
      case e: Exception => {
        e.printStackTrace()
        false
      }
    }
  }

  /**
   * Save the wiki page.
   */
  def saveWikiPage(owner: String, repository: String, currentPageName: String, newPageName: String,
      content: String, committer: model.Account, message: String, currentId: Option[String]): Option[String] = {
    LockUtil.lock(s"${owner}/${repository}/wiki"){
      using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
        val builder  = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headId   = git.getRepository.resolve(Constants.HEAD + "^{commit}")
        var created  = true
        var updated  = false
        var removed  = false

        if(headId != null){
          JGitUtil.processTree(git, headId){ (path, tree) =>
            if(path == currentPageName + ".md" && currentPageName != newPageName){
              removed = true
            } else if(path != newPageName + ".md"){
              builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
            } else {
              created = false
              updated = JGitUtil.getContentFromId(git, tree.getEntryObjectId, true).map(new String(_, "UTF-8") != content).getOrElse(false)
            }
          }
        }

        if(created || updated || removed){
          builder.add(JGitUtil.createDirCacheEntry(newPageName + ".md", FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8"))))
          builder.finish()
          val newHeadId = JGitUtil.createNewCommit(git, inserter, headId, builder.getDirCache.writeTree(inserter), committer.fullName, committer.mailAddress,
            if(message.trim.length == 0) {
              if(removed){
                s"Rename ${currentPageName} to ${newPageName}"
              } else if(created){
                s"Created ${newPageName}"
              } else {
                s"Updated ${newPageName}"
              }
            } else {
              message
            })

          Some(newHeadId.getName)
        } else None
      }
    }
  }

  /**
   * Delete the wiki page.
   */
  def deleteWikiPage(owner: String, repository: String, pageName: String,
                     committer: String, mailAddress: String, message: String): Unit = {
    LockUtil.lock(s"${owner}/${repository}/wiki"){
      using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
        val builder  = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headId   = git.getRepository.resolve(Constants.HEAD + "^{commit}")
        var removed  = false

        JGitUtil.processTree(git, headId){ (path, tree) =>
          if(path != pageName + ".md"){
            builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
          } else {
            removed = true
          }
        }
        if(removed){
          builder.finish()
          JGitUtil.createNewCommit(git, inserter, headId, builder.getDirCache.writeTree(inserter), committer, mailAddress, message)
        }
      }
    }
  }

}
