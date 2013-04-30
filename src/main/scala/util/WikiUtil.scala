package util

import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils

object WikiUtil {
  
  /**
   * The model for wiki page.
   * 
   * @param name the page name
   * @param content the page content
   */
  case class WikiPageInfo(name: String, content: String)
  
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

  
  /**
   * Returns the wiki page.
   */
  def getPage(owner: String, repository: String, pageName: String): Option[WikiPageInfo] = {
    val git = Git.open(getWikiRepositoryDir(owner, repository))
    JGitUtil.getFileList(git, "master", ".").find(_.name == pageName).map { file =>
      WikiPageInfo(file.name, new String(git.getRepository.open(file.id).getBytes, "UTF-8"))
    }
  }
  
  // TODO 
  //def getPageHistory(owner: String, repository: String, pageName: String): List[WikiPageHistoryInfo]
  
  // TODO synchronized
  /**
   * Save the wiki page.
   */
  def savePage(owner: String, repository: String, pageName: String, content: String, committer: String, message: String): Unit = {
    val workDir = getWikiWorkDir(owner, repository)
    
    // clone
    if(!workDir.exists){
      Git.cloneRepository.setURI(getWikiRepositoryDir(owner, repository).toURI.toString).setDirectory(workDir).call
    }
    
    // write as file
    val file = new File(workDir, pageName + ".md")
    FileUtils.writeStringToFile(file, content, "UTF-8")
    
    // commit and push
    val cloned = Git.open(workDir)
    cloned.add.addFilepattern(file.getName).call
    cloned.commit.setAuthor(committer, committer + "@devnull").setMessage(message).call
    cloned.push.call
  }
  
}