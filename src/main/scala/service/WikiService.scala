package service

import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import util.JGitUtil.DiffInfo
import util.{Directory, JGitUtil}
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.util.concurrent.ConcurrentHashMap

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

  /**
   * lock objects
   */
  private val locks = new ConcurrentHashMap[String, AnyRef]()

  /**
   * Returns the lock object for the specified repository.
   */
  private def getLockObject(owner: String, repository: String): AnyRef = synchronized {
    val key = owner + "/" + repository
    if(!locks.containsKey(key)){
      locks.put(key, new AnyRef())
    }
    locks.get(key)
  }

  /**
   * Synchronizes a given function which modifies the working copy of the wiki repository.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param f the function which modifies the working copy of the wiki repository
   * @tparam T the return type of the given function
   * @return the result of the given function
   */
  def lock[T](owner: String, repository: String)(f: => T): T = getLockObject(owner, repository).synchronized(f)

}

trait WikiService {
  import WikiService._

  def createWikiRepository(owner: model.Account, repository: String): Unit = {
    lock(owner.userName, repository){
      val dir = Directory.getWikiRepositoryDir(owner.userName, repository)
      if(!dir.exists){
        try {
          JGitUtil.initRepository(dir)
          saveWikiPage(owner.userName, repository, "Home", "Home", s"Welcome to the ${repository} wiki!!", owner, "Initial Commit")
        } finally {
          // once delete cloned repository because initial cloned repository does not have 'branch.master.merge'
          FileUtils.deleteDirectory(Directory.getWikiWorkDir(owner.userName, repository))
        }
      }
    }
  }
  
  /**
   * Returns the wiki page.
   */
  def getWikiPage(owner: String, repository: String, pageName: String): Option[WikiPageInfo] = {
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

  /**
   * Returns the content of the specified file.
   */
  def getFileContent(owner: String, repository: String, path: String): Option[Array[Byte]] = {
    JGitUtil.withGit(Directory.getWikiRepositoryDir(owner, repository)){ git =>
      try {
        val index = path.lastIndexOf('/')
        val parentPath = if(index < 0) "."  else path.substring(0, index)
        val fileName   = if(index < 0) path else path.substring(index + 1)

        JGitUtil.getFileList(git, "master", parentPath).find(_.name == fileName).map { file =>
          git.getRepository.open(file.id).getBytes
        }
      } catch {
        // TODO no commit, but it should not judge by exception.
        case e: NullPointerException => None
      }
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

    lock(owner, repository){
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
  def deleteWikiPage(owner: String, repository: String, pageName: String, committer: String, message: String): Unit = {
    lock(owner, repository){
      // clone working copy
      val workDir = Directory.getWikiWorkDir(owner, repository)
      cloneOrPullWorkingCopy(workDir, owner, repository)

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
  }

  /**
   * Returns differences between specified commits.
   */
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