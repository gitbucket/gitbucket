package service

import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import util.{StringUtil, Directory, JGitUtil, LockUtil}
import util.ControlUtil._
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.api.errors.PatchApplyException

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
      defining(Directory.getWikiWorkDir(owner, repository)){ workDir =>
        // clone working copy
        cloneOrPullWorkingCopy(workDir, owner, repository)

        using(Git.open(workDir)){ git =>
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

          try {
            git.apply.setPatch(new java.io.ByteArrayInputStream(patch.getBytes("UTF-8"))).call
            git.add.addFilepattern(".").call
            git.commit.setCommitter(committer.userName, committer.mailAddress).setMessage(pageName match {
              case Some(x) => s"Revert ${from} ... ${to} on ${x}"
              case None    => s"Revert ${from} ... ${to}"
            }).call
            git.push.call
            true
          } catch {
            case ex: PatchApplyException => false
          }
        }
      }
    }
  }


  /**
   * Save the wiki page.
   */
  def saveWikiPage(owner: String, repository: String, currentPageName: String, newPageName: String,
      content: String, committer: model.Account, message: String, currentId: Option[String]): Option[String] = {

    LockUtil.lock(s"${owner}/${repository}/wiki"){
      defining(Directory.getWikiWorkDir(owner, repository)){ workDir =>
        // clone working copy
        cloneOrPullWorkingCopy(workDir, owner, repository)

        // write as file
        using(Git.open(workDir)){ git =>
          defining(new File(workDir, newPageName + ".md")){ file =>
            // new page
            val created = !file.exists

            // created or updated
            val added = executeIf(!file.exists || FileUtils.readFileToString(file, "UTF-8") != content){
              FileUtils.writeStringToFile(file, content, "UTF-8")
              git.add.addFilepattern(file.getName).call
            }

            // delete file
            val deleted = executeIf(currentPageName != "" && currentPageName != newPageName){
              git.rm.addFilepattern(currentPageName + ".md").call
            }

            // commit and push
            optionIf(added || deleted){
              defining(git.commit.setCommitter(committer.userName, committer.mailAddress)
                .setMessage(if(message.trim.length == 0){
                    if(deleted){
                      s"Rename ${currentPageName} to ${newPageName}"
                    } else if(created){
                      s"Created ${newPageName}"
                    } else {
                      s"Updated ${newPageName}"
                    }
                  } else {
                    message
                  }).call){ commit =>
                git.push.call
                Some(commit.getName)
              }
            }
          }
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
      defining(Directory.getWikiWorkDir(owner, repository)){ workDir =>
        // clone working copy
        cloneOrPullWorkingCopy(workDir, owner, repository)

        // delete file
        new File(workDir, pageName + ".md").delete

        using(Git.open(workDir)){ git =>
          git.rm.addFilepattern(pageName + ".md").call

          // commit and push
          git.commit.setCommitter(committer, mailAddress).setMessage(message).call
          git.push.call
        }
      }
    }
  }

  private def cloneOrPullWorkingCopy(workDir: File, owner: String, repository: String): Unit = {
    if(!workDir.exists){
      Git.cloneRepository
        .setURI(Directory.getWikiRepositoryDir(owner, repository).toURI.toString)
        .setDirectory(workDir)
        .call
        .getRepository
        .close
    } else using(Git.open(workDir)){ git =>
      git.pull.call
    }
  }

}