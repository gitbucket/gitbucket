package util

import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.lib.RepositoryBuilder

object WikiUtil {
  
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
  
  /**
   * Returns the directory of the wiki repository.
   */
  def getWikiRepositoryDir(owner: String, repository: String): File =
    new File("%s/%s/%s-wiki.git".format(Directory.RepositoryHome, owner, repository))
  
  /**
   * Returns the directory of the wiki working directory which is cloned from the wiki repository.
   */
  def getWikiWorkDir(owner: String, repository: String): File = 
    new File("%s/tmp/%s/%s-wiki".format(Directory.RepositoryHome, owner, repository))

  // TODO synchronized?
  def createWikiRepository(owner: String, repository: String): Unit = {
    val dir = getWikiRepositoryDir(owner, repository)
    if(!dir.exists){
      val repo = new RepositoryBuilder().setGitDir(dir).setBare.build
      repo.create
      savePage(owner, repository, "Home", "Home", "Welcome to the %s wiki!!".format(repository), owner, "Initial Commit")
    }
  }
  
  /**
   * Returns the wiki page.
   */
  def getPage(owner: String, repository: String, pageName: String): Option[WikiPageInfo] = {
    // TODO create wiki repository in the repository setting changing.
    createWikiRepository(owner, repository)
    
    val git = Git.open(getWikiRepositoryDir(owner, repository))
    try {
      JGitUtil.getFileList(git, "master", ".").find(_.name == pageName + ".md").map { file =>
        WikiPageInfo(file.name, new String(git.getRepository.open(file.id).getBytes, "UTF-8"), file.committer, file.time)
      }
    } catch {
      // TODO no commit, but it should not judge by exception.
      case e: NullPointerException => None
    }
  }
  
  // TODO
  // def getPageList(owner: String, repository: String): List[WikiPageHistoryInfo]
  
  // TODO 
  //def getPageHistory(owner: String, repository: String, pageName: String): List[WikiPageHistoryInfo]
  
  // TODO synchronized
  /**
   * Save the wiki page.
   */
  def savePage(owner: String, repository: String, currentPageName: String, newPageName: String,
      content: String, committer: String, message: String): Unit = {
    
    // TODO create wiki repository in the repository setting changing.
    createWikiRepository(owner, repository)
    
    val workDir = getWikiWorkDir(owner, repository)
    
    // clone
    if(!workDir.exists){
      Git.cloneRepository.setURI(getWikiRepositoryDir(owner, repository).toURI.toString).setDirectory(workDir).call
    }
    
    // write as file
    val cloned = Git.open(workDir)
    val file = new File(workDir, newPageName + ".md")
    FileUtils.writeStringToFile(file, content, "UTF-8")
    cloned.add.addFilepattern(file.getName).call
    
    // delete file
    if(currentPageName != newPageName){
      cloned.rm.addFilepattern(currentPageName + ".md")
    }
    
    // commit and push
    cloned.commit.setAuthor(committer, committer + "@devnull").setMessage(message).call
    cloned.push.call
  }
  
}