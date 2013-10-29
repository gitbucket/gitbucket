package service

import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import util.{PatchUtil, Directory, JGitUtil, LockUtil}
import _root_.util.ControlUtil._
import org.eclipse.jgit.treewalk.{TreeWalk, CanonicalTreeParser}
import org.eclipse.jgit.lib._
import org.eclipse.jgit.dircache.{DirCache, DirCacheEntry}
import org.eclipse.jgit.merge.{ResolveMerger, MergeStrategy}
import org.eclipse.jgit.revwalk.RevWalk
import scala.collection.JavaConverters._
import org.eclipse.jgit.diff.{DiffEntry, DiffFormatter}
import java.io.ByteArrayInputStream
import org.eclipse.jgit.patch._
import org.eclipse.jgit.api.errors.PatchFormatException
import scala.collection.JavaConverters._


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

}

trait WikiService {
  import WikiService._

  def createWikiRepository(loginAccount: model.Account, owner: String, repository: String): Unit =
    LockUtil.lock(s"${owner}/${repository}/wiki"){
      defining(Directory.getWikiRepositoryDir(owner, repository)){ dir =>
        if(!dir.exists){
          try {
            JGitUtil.initRepository(dir)
            saveWikiPage(owner, repository, "Home", "Home", s"Welcome to the ${repository} wiki!!", loginAccount, "Initial Commit", None)
          } finally {
            // once delete cloned repository because initial cloned repository does not have 'branch.master.merge'
            FileUtils.deleteDirectory(Directory.getWikiWorkDir(owner, repository))
          }
        }
      }
    }

  /**
   * Returns the wiki page.
   */
  def getWikiPage(owner: String, repository: String, pageName: String): Option[WikiPageInfo] = {
    using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
      optionIf(!JGitUtil.isEmpty(git)){
        JGitUtil.getFileList(git, "master", ".").find(_.name == pageName + ".md").map { file =>
          WikiPageInfo(file.name, new String(git.getRepository.open(file.id).getBytes, "UTF-8"), file.committer, file.time, file.commitId)
        }
      }
    }
  }

  /**
   * Returns the content of the specified file.
   */
  def getFileContent(owner: String, repository: String, path: String): Option[Array[Byte]] =
    using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
      optionIf(!JGitUtil.isEmpty(git)){
        val index = path.lastIndexOf('/')
        val parentPath = if(index < 0) "."  else path.substring(0, index)
        val fileName   = if(index < 0) path else path.substring(index + 1)

        JGitUtil.getFileList(git, "master", parentPath).find(_.name == fileName).map { file =>
          git.getRepository.open(file.id).getBytes
        }
      }
    }

  /**
   * Returns the list of wiki page names.
   */
  def getWikiPageList(owner: String, repository: String): List[String] = {
    using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
      JGitUtil.getFileList(git, "master", ".")
        .filter(_.name.endsWith(".md"))
        .map(_.name.replaceFirst("\\.md$", ""))
        .sortBy(x => x)
    }
  }

  /**
   * Reverts specified changes.
   */
  def revertWikiPage(owner: String, repository: String, from: String, to: String,
                     committer: model.Account, pageName: Option[String]): Boolean = {

    LockUtil.lock(s"${owner}/${repository}/wiki"){
      using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
        val reader = git.getRepository.newObjectReader
        val oldTreeIter = new CanonicalTreeParser
        oldTreeIter.reset(reader, git.getRepository.resolve(from + "^{tree}"))

        val newTreeIter = new CanonicalTreeParser
        newTreeIter.reset(reader, git.getRepository.resolve(to + "^{tree}"))

        import scala.collection.JavaConverters._
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
              val page = getWikiPage(owner, repository, fh.getNewPath.replaceFirst("\\.md$", "")).get
              Seq(RevertInfo("ADD", fh.getNewPath, PatchUtil.apply(page.content, fh)))
            }
            case DiffEntry.ChangeType.ADD => {
              Seq(RevertInfo("ADD", fh.getNewPath, PatchUtil.apply("", fh)))
            }
            case DiffEntry.ChangeType.DELETE => {
              Seq(RevertInfo("DELETE", fh.getNewPath, ""))
            }
            case DiffEntry.ChangeType.RENAME => {
              Seq(
                RevertInfo("DELETE", fh.getOldPath, ""),
                RevertInfo("ADD", fh.getNewPath, PatchUtil.apply("", fh))
              )
            }
            case _ => Nil
          }
        }).flatten

        revertInfo.foreach { revert =>
          println(revert)
        }

//        val source = getWikiPage(owner, repository, pageName.get)
//        PatchUtil.applyToFile(PatchUtil.createPatch(patch), source.get.content, pageName + ".md")

//          try {
//            git.apply.setPatch(new java.io.ByteArrayInputStream(patch.getBytes("UTF-8"))).call
//            git.add.addFilepattern(".").call
//            git.commit.setCommitter(committer.fullName, committer.mailAddress).setMessage(pageName match {
//              case Some(x) => s"Revert ${from} ... ${to} on ${x}"
//              case None    => s"Revert ${from} ... ${to}"
//            }).call
//            git.push.call
//            true
//          } catch {
//            case ex: PatchApplyException => false
//          }
      }
    }



//    LockUtil.lock(s"${owner}/${repository}/wiki"){
//      defining(Directory.getWikiWorkDir(owner, repository)){ workDir =>
//        // clone working copy
//        cloneOrPullWorkingCopy(workDir, owner, repository)
//
//        using(Git.open(workDir)){ git =>
//          val reader = git.getRepository.newObjectReader
//          val oldTreeIter = new CanonicalTreeParser
//          oldTreeIter.reset(reader, git.getRepository.resolve(from + "^{tree}"))
//
//          val newTreeIter = new CanonicalTreeParser
//          newTreeIter.reset(reader, git.getRepository.resolve(to + "^{tree}"))
//
//          import scala.collection.JavaConverters._
//          val diffs = git.diff.setNewTree(oldTreeIter).setOldTree(newTreeIter).call.asScala.filter { diff =>
//            pageName match {
//              case Some(x) => diff.getNewPath == x + ".md"
//              case None    => true
//            }
//          }
//
//          val patch = using(new java.io.ByteArrayOutputStream()){ out =>
//            val formatter = new DiffFormatter(out)
//            formatter.setRepository(git.getRepository)
//            formatter.format(diffs.asJava)
//            new String(out.toByteArray, "UTF-8")
//          }
//
//          try {
//            git.apply.setPatch(new java.io.ByteArrayInputStream(patch.getBytes("UTF-8"))).call
//            git.add.addFilepattern(".").call
//            git.commit.setCommitter(committer.fullName, committer.mailAddress).setMessage(pageName match {
//              case Some(x) => s"Revert ${from} ... ${to} on ${x}"
//              case None    => s"Revert ${from} ... ${to}"
//            }).call
//            git.push.call
//            true
//          } catch {
//            case ex: PatchApplyException => false
//          }
//        }
//      }
//    }
    true
  }


  /**
   * Save the wiki page.
   */
  def saveWikiPage(owner: String, repository: String, currentPageName: String, newPageName: String,
      content: String, committer: model.Account, message: String, currentId: Option[String]): Option[String] = {

    LockUtil.lock(s"${owner}/${repository}/wiki"){
      using(Git.open(Directory.getWikiRepositoryDir(owner, repository))){ git =>
        val repo     = git.getRepository()
        val dirCache = DirCache.newInCore()
        val builder  = dirCache.builder()
        val inserter = repo.newObjectInserter()
        val headId   = repo.resolve(Constants.HEAD + "^{commit}")
        var created  = true
        var updated  = false
        var removed  = false

        using(new RevWalk(git.getRepository)){ revWalk =>
          val treeWalk = new TreeWalk(repo)
          val index    = treeWalk.addTree(revWalk.parseTree(headId))
          treeWalk.setRecursive(true)
          while(treeWalk.next){
            val path = treeWalk.getPathString
            val tree = treeWalk.getTree(index, classOf[CanonicalTreeParser])
            if(path == currentPageName + ".md" && currentPageName != newPageName){
              removed = true
            } else if(path != newPageName + ".md"){
              val entry = new DirCacheEntry(path)
              entry.setObjectId(tree.getEntryObjectId())
              entry.setFileMode(tree.getEntryFileMode())
              builder.add(entry)
            } else {
              created = false
              updated = JGitUtil.getContent(git, tree.getEntryObjectId, true).map(new String(_, "UTF-8") != content).getOrElse(false)
            }
          }
          treeWalk.release()
        }

        optionIf(created || updated || removed){
          val entry = new DirCacheEntry(newPageName + ".md")
          entry.setFileMode(FileMode.REGULAR_FILE)
          entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8")))
          builder.add(entry)

          builder.finish()
          val treeId = dirCache.writeTree(inserter)

          val newCommit = new CommitBuilder()
          newCommit.setCommitter(new PersonIdent(committer.fullName, committer.mailAddress))
          newCommit.setAuthor(new PersonIdent(committer.fullName, committer.mailAddress))
          newCommit.setMessage(if(message.trim.length == 0) {
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
          newCommit.setParentIds(List(headId).asJava)
          newCommit.setTreeId(treeId)

          val newHeadId = inserter.insert(newCommit)
          inserter.flush()

          val refUpdate = repo.updateRef(Constants.HEAD)
          refUpdate.setNewObjectId(newHeadId)
          refUpdate.update()

          Some(newHeadId.getName)
        }
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
          val repo     = git.getRepository()
          val dirCache = DirCache.newInCore()
          val builder  = dirCache.builder()
          val inserter = repo.newObjectInserter()
          val headId   = repo.resolve(Constants.HEAD + "^{commit}")
          var removed  = false

          using(new RevWalk(git.getRepository)){ revWalk =>
            val treeWalk = new TreeWalk(repo)
            val index    = treeWalk.addTree(revWalk.parseTree(headId))
            treeWalk.setRecursive(true)
            while(treeWalk.next){
              val path = treeWalk.getPathString
              val tree = treeWalk.getTree(index, classOf[CanonicalTreeParser])
              if(path != pageName + ".md"){
                val entry = new DirCacheEntry(path)
                entry.setObjectId(tree.getEntryObjectId())
                entry.setFileMode(tree.getEntryFileMode())
                builder.add(entry)
              } else {
                removed = true
              }
            }
            treeWalk.release()
          }

          if(removed){
            builder.finish()
            val treeId = dirCache.writeTree(inserter)

            val newCommit = new CommitBuilder()
            newCommit.setCommitter(new PersonIdent(committer, mailAddress))
            newCommit.setAuthor(new PersonIdent(committer, mailAddress))
            newCommit.setMessage(message)
            newCommit.setParentIds(List(headId).asJava)
            newCommit.setTreeId(treeId)

            val newHeadId = inserter.insert(newCommit)
            inserter.flush()

            val refUpdate = repo.updateRef(Constants.HEAD)
            refUpdate.setNewObjectId(newHeadId)
            refUpdate.update()

          }
        }
      }
  }

  case class RevertInfo(operation: String, filePath: String, source: String)

//  private def cloneOrPullWorkingCopy(workDir: File, owner: String, repository: String): Unit = {
//    if(!workDir.exists){
//      Git.cloneRepository
//        .setURI(Directory.getWikiRepositoryDir(owner, repository).toURI.toString)
//        .setDirectory(workDir)
//        .call
//        .getRepository
//        .close
//    } else using(Git.open(workDir)){ git =>
//      git.pull.call
//    }
//  }

}
