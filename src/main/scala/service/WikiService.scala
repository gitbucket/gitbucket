package service

import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import util.{Directory, JGitUtil, LockUtil}

object WikiService {
  
  /**
   * The model for wiki page.
   * 
   * @param name the page name
   * @param content the page content
   * @param committer the last committer
   * @param time the last modified time
   */
  case class WikiPageInfo(name: String, content: String, committer: String, time: Date)
  
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

  def createWikiRepository(loginAccount: model.Account, owner: String, repository: String): Unit = {
    LockUtil.lock(s"${owner}/${repository}/wiki"){
      val dir = Directory.getWikiRepositoryDir(owner, repository)
      if(!dir.exists){
        try {
          JGitUtil.initRepository(dir)
          saveWikiPage(owner, repository, "Home", "Home", s"Welcome to the ${repository} wiki!!", loginAccount, "Initial Commit")
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
    JGitUtil.withGit(Directory.getWikiRepositoryDir(owner, repository)){ git =>
      if(!JGitUtil.isEmpty(git)){
        JGitUtil.getFileList(git, "master", ".").find(_.name == pageName + ".md").map { file =>
          WikiPageInfo(file.name, new String(git.getRepository.open(file.id).getBytes, "UTF-8"), file.committer, file.time)
        }
      } else None
    }
  }

  /**
   * Returns the content of the specified file.
   */
  def getFileContent(owner: String, repository: String, path: String): Option[Array[Byte]] = {
    JGitUtil.withGit(Directory.getWikiRepositoryDir(owner, repository)){ git =>
      if(!JGitUtil.isEmpty(git)){
        val index = path.lastIndexOf('/')
        val parentPath = if(index < 0) "."  else path.substring(0, index)
        val fileName   = if(index < 0) path else path.substring(index + 1)

        JGitUtil.getFileList(git, "master", parentPath).find(_.name == fileName).map { file =>
          git.getRepository.open(file.id).getBytes
        }
      } else None
    }
  }

  /**
   * Returns the list of wiki page names.
   */
  def getWikiPageList(owner: String, repository: String): List[String] = {
    JGitUtil.withGit(Directory.getWikiRepositoryDir(owner, repository)){ git =>
      JGitUtil.getFileList(git, "master", ".")
        .filter(_.name.endsWith(".md"))
        .map(_.name.replaceFirst("\\.md$", ""))
        .sortBy(x => x)
    }
  }
  
  /**
   * Save the wiki page.
   */
  def saveWikiPage(owner: String, repository: String, currentPageName: String, newPageName: String,
      content: String, committer: model.Account, message: String): Unit = {

    LockUtil.lock(s"${owner}/${repository}/wiki"){
      // clone working copy
      val workDir = Directory.getWikiWorkDir(owner, repository)
      cloneOrPullWorkingCopy(workDir, owner, repository)

      // write as file
      JGitUtil.withGit(workDir){ git =>
        val file = new File(workDir, newPageName + ".md")
        val added = if(!file.exists || FileUtils.readFileToString(file, "UTF-8") != content){
          FileUtils.writeStringToFile(file, content, "UTF-8")
          git.add.addFilepattern(file.getName).call
          true
        } else {
          false
        }

        // delete file
        val deleted = if(currentPageName != "" && currentPageName != newPageName){
          git.rm.addFilepattern(currentPageName + ".md").call
          true
        } else {
          false
        }

        // commit and push
        if(added || deleted){
          git.commit.setCommitter(committer.userName, committer.mailAddress).setMessage(message).call
          git.push.call
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
      // clone working copy
      val workDir = Directory.getWikiWorkDir(owner, repository)
      cloneOrPullWorkingCopy(workDir, owner, repository)

      // delete file
      new File(workDir, pageName + ".md").delete
    
      JGitUtil.withGit(workDir){ git =>
        git.rm.addFilepattern(pageName + ".md").call
    
        // commit and push
        git.commit.setAuthor(committer, mailAddress).setMessage(message).call
        git.push.call
      }
    }
  }

  private def cloneOrPullWorkingCopy(workDir: File, owner: String, repository: String): Unit = {
    if(!workDir.exists){
      val git =
        Git.cloneRepository
          .setURI(Directory.getWikiRepositoryDir(owner, repository).toURI.toString)
          .setDirectory(workDir)
          .call
      git.getRepository.close  // close .git resources.
    } else {
      JGitUtil.withGit(workDir){ git =>
        git.pull.call
      }
    }
  }

}