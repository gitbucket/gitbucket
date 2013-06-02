package service

import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import app.DiffInfo
import util.{Directory, JGitUtil}
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser

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
  
//  /**
//   * Returns the directory of the wiki repository.
//   */
//  def getWikiRepositoryDir(owner: String, repository: String): File =
//    new File("%s/%s/%s.wiki.git".format(Directory.RepositoryHome, owner, repository))
//  
//  /**
//   * Returns the directory of the wiki working directory which is cloned from the wiki repository.
//   */
//  def getWikiWorkDir(owner: String, repository: String): File = 
//    new File("%s/tmp/%s/%s.wiki".format(Directory.GitBucketHome, owner, repository))

  // TODO synchronized?
  def createWikiRepository(owner: String, repository: String): Unit = {
    val dir = Directory.getWikiRepositoryDir(owner, repository)
    if(!dir.exists){
      val repo = new RepositoryBuilder().setGitDir(dir).setBare.build
      try {
        repo.create
        saveWikiPage(owner, repository, "Home", "Home", "Welcome to the %s wiki!!".format(repository), owner, "Initial Commit")
      } finally {
        repo.close
      }
    }
  }
  
  /**
   * Returns the wiki page.
   */
  def getWikiPage(owner: String, repository: String, pageName: String): Option[WikiPageInfo] = {
    // TODO create wiki repository in the repository setting changing.
    createWikiRepository(owner, repository)
    
    JGitUtil.withGit(Directory.getWikiRepositoryDir(owner, repository)){ git =>
      try {
        JGitUtil.getFileList(git, "master", ".").find(_.name == pageName + ".md").map { file =>
          WikiPageInfo(file.name, new String(git.getRepository.open(file.id).getBytes, "UTF-8"), file.committer, file.time)
        }
      } catch {
        // TODO no commit, but it should not judge by exception.
        case e: NullPointerException => None
      }
    }
  }
  
  def getWikiPageList(owner: String, repository: String): List[String] = {
    JGitUtil.getFileList(Git.open(Directory.getWikiRepositoryDir(owner, repository)), "master", ".")
      .filter(_.name.endsWith(".md"))
      .map(_.name.replaceFirst("\\.md$", ""))
      .sortBy(x => x)
  }
  
  // TODO synchronized
  /**
   * Save the wiki page.
   */
  def saveWikiPage(owner: String, repository: String, currentPageName: String, newPageName: String,
      content: String, committer: String, message: String): Unit = {
    
    // TODO create wiki repository in the repository setting changing.
    createWikiRepository(owner, repository)
    
    val workDir = Directory.getWikiWorkDir(owner, repository)
    
    // clone
    if(!workDir.exists){
      Git.cloneRepository
        .setURI(Directory.getWikiRepositoryDir(owner, repository).toURI.toString)
        .setDirectory(workDir)
        .call
    }
    
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
        // TODO committer's mail address
        git.commit.setAuthor(committer, committer + "@devnull").setMessage(message).call
        git.push.call
      }
    }
  }
  
  /**
   * Delete the wiki page.
   */
  def deleteWikiPage(owner: String, repository: String, pageName: String, committer: String, message: String): Unit = {
    // TODO create wiki repository in the repository setting changing.
    createWikiRepository(owner, repository)
    
    val workDir = Directory.getWikiWorkDir(owner, repository)
    
    // clone
    if(!workDir.exists){
      Git.cloneRepository
        .setURI(Directory.getWikiRepositoryDir(owner, repository).toURI.toString)
        .setDirectory(workDir)
        .call
    }
    
    // delete file
    new File(workDir, pageName + ".md").delete
    
    JGitUtil.withGit(workDir){ git =>
      git.rm.addFilepattern(pageName + ".md").call
    
      // commit and push
      // TODO committer's mail address
      git.commit.setAuthor(committer, committer + "@devnull").setMessage(message).call
      git.push.call
    }
  }
  
  def getWikiDiffs(git: Git, commitId1: String, commitId2: String): List[DiffInfo] = {
      // get diff between specified commit and its previous commit
      val reader = git.getRepository.newObjectReader
      
      val oldTreeIter = new CanonicalTreeParser
      oldTreeIter.reset(reader, git.getRepository.resolve(commitId1 + "^{tree}"))
      
      val newTreeIter = new CanonicalTreeParser
      newTreeIter.reset(reader, git.getRepository.resolve(commitId2 + "^{tree}"))
      
      import scala.collection.JavaConverters._
      git.diff.setNewTree(newTreeIter).setOldTree(oldTreeIter).call.asScala.map { diff =>
        DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath,
            JGitUtil.getContent(git, diff.getOldId.toObjectId, false).map(new String(_, "UTF-8")), 
            JGitUtil.getContent(git, diff.getNewId.toObjectId, false).map(new String(_, "UTF-8")))
      }.toList
  }   
}
