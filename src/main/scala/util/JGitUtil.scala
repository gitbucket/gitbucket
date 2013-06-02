package util

import org.eclipse.jgit.api.Git
import util.Directory._
import scala.collection.JavaConverters._
import javax.servlet.ServletContext
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.diff._
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.eclipse.jgit.errors.MissingObjectException
import java.util.Date

/**
 * Provides complex JGit operations.
 */
object JGitUtil {

  /**
   * The repository data.
   *
   * @param owner the user name of the repository owner
   * @param name the repository name
   * @param url the repository URL
   * @param branchList the list of branch names
   * @param tags the list of tags
   */
  case class RepositoryInfo(owner: String, name: String, url: String, branchList: List[String], tags: List[TagInfo])

  /**
   * The file data for the file list of the repository viewer.
   *
   * @param id the object id
   * @param isDirectory whether is it directory
   * @param name the file (or directory) name
   * @param time the last modified time
   * @param message the last commit message
   * @param committer the last committer name
   */
  case class FileInfo(id: ObjectId, isDirectory: Boolean, name: String, time: Date, message: String, committer: String)

  /**
   * The commit data.
   *
   * @param id the commit id
   * @param time the commit time
   * @param committer  the commiter name
   * @param message the commit message
   */
  case class CommitInfo(id: String, time: Date, committer: String, message: String){
    def this(rev: org.eclipse.jgit.revwalk.RevCommit) =
      this(rev.getName, rev.getCommitterIdent.getWhen, rev.getCommitterIdent.getName, rev.getFullMessage)
  }

  case class DiffInfo(changeType: ChangeType, oldPath: String, newPath: String, oldContent: Option[String], newContent: Option[String])

  /**
   * The file content data for the file content view of the repository viewer.
   *
   * @param viewType "image", "large" or "other"
   * @param content the string content
   */
  case class ContentInfo(viewType: String, content: Option[String])

  /**
   * The tag data.
   *
   * @param name the tag name
   * @param time the tagged date
   * @param id the commit id
   */
  case class TagInfo(name: String, time: Date, id: String)

  /**
   * Use this method to use the Git object.
   * Repository resources are released certainly after processing.
   */
  def withGit[T](dir: java.io.File)(f: Git => T): T = withGit(Git.open(dir))(f)
  
  /**
   * Use this method to use the Git object.
   * Repository resources are released certainly after processing.
   */
  def withGit[T](git: Git)(f: Git => T): T = {
    try {
      f(git)
    } finally {
      git.getRepository.close
    }
  }
  
  /**
   * Returns RevCommit from the commit id.
   * 
   * @param git the Git object
   * @param commitId the ObjectId of the commit
   * @return the RevCommit for the specified commit
   */
  def getRevCommitFromId(git: Git, commitId: ObjectId): RevCommit = {
    val revWalk = new RevWalk(git.getRepository)
    val revCommit = revWalk.parseCommit(commitId)
    revWalk.dispose
    revCommit
  }
  
  /**
   * Returns the repository information. It contains branch names and tag names.
   */
  def getRepositoryInfo(owner: String, repository: String, servletContext: ServletContext): RepositoryInfo = {
    withGit(getRepositoryDir(owner, repository)){ git =>
      RepositoryInfo(
        owner, repository, "http://localhost:8080%s/git/%s/%s.git".format(servletContext.getContextPath, owner, repository),
        // branches
        git.branchList.call.asScala.map { ref =>
          ref.getName.replaceFirst("^refs/heads/", "")
        }.toList,
        // tags
        git.tagList.call.asScala.map { ref =>
          val revCommit = getRevCommitFromId(git, ref.getObjectId)
          TagInfo(ref.getName.replaceFirst("^refs/tags/", ""), revCommit.getCommitterIdent.getWhen, revCommit.getName)
        }.toList
      )
    }
  }
  
  /**
   * Returns the file list of the specified path.
   * 
   * @param git the Git object
   * @param revision the branch name or commit id
   * @param path the directory path (optional)
   * @return HTML of the file list
   */
  def getFileList(git: Git, revision: String, path: String = "."): List[FileInfo] = {
    val revWalk = new RevWalk(git.getRepository)
    val objectId = git.getRepository.resolve(revision)
    val revCommit = revWalk.parseCommit(objectId)
      
    val treeWalk = new TreeWalk(git.getRepository)
    treeWalk.addTree(revCommit.getTree)
    if(path != "."){
      treeWalk.setRecursive(true)
      treeWalk.setFilter(PathFilter.create(path))
    }
      
    val list = new scala.collection.mutable.ListBuffer[FileInfo]
    
    while (treeWalk.next()) {
      val fileCommit = JGitUtil.getLatestCommitFromPath(git, treeWalk.getPathString, revision)
      list.append(FileInfo(
          treeWalk.getObjectId(0),
          treeWalk.getFileMode(0) == FileMode.TREE, 
          treeWalk.getNameString,
          fileCommit.getCommitterIdent.getWhen,
          fileCommit.getShortMessage, 
          fileCommit.getCommitterIdent.getName)
      )
    }
    
    treeWalk.release
    revWalk.dispose
    
    list.toList.sortWith { (file1, file2) => (file1.isDirectory, file2.isDirectory) match {
      case (true , false) => true
      case (false, true ) => false
      case _ => file1.name.compareTo(file2.name) < 0
    }}
  }
  
  /**
   * Returns the commit list of the specified branch.
   * 
   * @param git the Git object
   * @param revision the branch name or commit id
   * @param page the page number (1-)
   * @param limit the number of commit info per page. 0 (default) means unlimited.
   * @param path filters by this path. default is no filter.
   * @return a tuple of the commit list and whether has next
   */
  def getCommitLog(git: Git, revision: String, page: Int = 1, limit: Int = 0, path: String = ""): (List[CommitInfo], Boolean) = {
    val fixedPage = if(page <= 0) 1 else page
    
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], count: Int, logs: List[CommitInfo]): (List[CommitInfo], Boolean)  =
      i.hasNext match {
        case true if(limit <= 0 || logs.size < limit) => 
          getCommitLog(i, count + 1, if(limit <= 0 || (fixedPage - 1) * limit < count) logs :+ new CommitInfo(i.next) else logs)
        case _ => (logs, i.hasNext)
      }
    
    val revWalk = new RevWalk(git.getRepository)
    revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(revision)))
    if(path.nonEmpty){
      revWalk.setRevFilter(new RevFilter(){
        def include(walk: RevWalk, commit: RevCommit): Boolean = {
          getDiffs(git, commit.getName).find(_.newPath == path).nonEmpty
        }
        override def clone(): RevFilter = this
      })
    }
    
    val commits = getCommitLog(revWalk.iterator, 0, Nil)
    revWalk.release
    
    commits
  }
  
  /**
   * Returns the commit list between two revisions.
   * 
   * @param git the Git object
   * @param from the from revision
   * @param to the to revision
   * @return the commit list
   */
  def getCommitLog(git: Git, from: String, to: String): List[CommitInfo] = {
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], logs: List[CommitInfo]): List[CommitInfo] =
      i.hasNext match {
        case true  => {
          val revCommit = i.next
          if(revCommit.name == from){
            logs 
          } else {
            getCommitLog(i, logs :+ new CommitInfo(revCommit))
          }
        }
        case false => logs
      }
    
    val revWalk = new RevWalk(git.getRepository)
    revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(to)))
    
    val commits = getCommitLog(revWalk.iterator, Nil)
    revWalk.release
    
    commits.reverse
  }
  
  
  /**
   * Returns the latest RevCommit of the specified path.
   * 
   * @param git the Git object
   * @param path the path
   * @param revision the branch name or commit id
   * @return the latest commit
   */
  def getLatestCommitFromPath(git: Git, path: String, revision: String): RevCommit = {
      val revWalk = new RevWalk(git.getRepository)
      revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(revision)))
      revWalk.sort(RevSort.REVERSE);
      val i = revWalk.iterator
      
      // TODO DON'T use var!
      var result: RevCommit = null
      
      while(i.hasNext){
        val commit = i.next
        if(commit.getParentCount == 0){
          // Initial commit
          val treeWalk = new TreeWalk(git.getRepository)
          treeWalk.reset()
          treeWalk.setRecursive(true)
          treeWalk.addTree(commit.getTree)
          while (treeWalk.next && result == null) {
            if(treeWalk.getPathString.startsWith(path)){
              result = commit
            }
          }
          treeWalk.release     
        } else {
          val parent = revWalk.parseCommit(commit.getParent(0).getId())
          val df = new DiffFormatter(DisabledOutputStream.INSTANCE)
          df.setRepository(git.getRepository)
          df.setDiffComparator(RawTextComparator.DEFAULT)
          df.setDetectRenames(true)
          val diffs = df.scan(parent.getTree(), commit.getTree)
          val find = diffs.asScala.find { diff =>
            val objectId = diff.getNewId.name
            (diff.getChangeType != ChangeType.DELETE && diff.getNewPath.startsWith(path))
          }
          if(find != None){
            result = commit
          }
        }
        revWalk.release
      }
      result
  }
  
  /**
   * Get object content of the given id as String from the Git repository.
   * 
   * @param git the Git object
   * @param id the object id
   * @param large if false then returns None for the large file
   * @return the object or None if object does not exist
   */
  def getContent(git: Git, id: ObjectId, large: Boolean): Option[Array[Byte]] = try {
    val loader = git.getRepository.getObjectDatabase.open(id)
    if(large == false && FileTypeUtil.isLarge(loader.getSize)){
      None
    } else {
      Some(git.getRepository.getObjectDatabase.open(id).getBytes)
    }
  } catch {
    case e: MissingObjectException => None
  }
  
  def getDiffs(git: Git, id: String): List[DiffInfo] = {
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], logs: List[RevCommit]): List[RevCommit] =
      i.hasNext match {
        case true if(logs.size < 2) => getCommitLog(i, logs :+ i.next)
        case _ => logs
      }
    
    val revWalk = new RevWalk(git.getRepository)
    revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(id)))
    
    val commits = getCommitLog(revWalk.iterator, Nil)
    revWalk.release
    
    val revCommit = commits(0)
    
    if(commits.length >= 2){
      // not initial commit
      val oldCommit = commits(1)
      
      // get diff between specified commit and its previous commit
      val reader = git.getRepository.newObjectReader
      
      val oldTreeIter = new CanonicalTreeParser
      oldTreeIter.reset(reader, git.getRepository.resolve(oldCommit.name + "^{tree}"))
      
      val newTreeIter = new CanonicalTreeParser
      newTreeIter.reset(reader, git.getRepository.resolve(id + "^{tree}"))
      
      import scala.collection.JavaConverters._
      git.diff.setNewTree(newTreeIter).setOldTree(oldTreeIter).call.asScala.map { diff =>
        DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath,
            JGitUtil.getContent(git, diff.getOldId.toObjectId, false).map(new String(_, "UTF-8")), 
            JGitUtil.getContent(git, diff.getNewId.toObjectId, false).map(new String(_, "UTF-8")))
      }.toList
    } else {
      // initial commit
      val walk = new TreeWalk(git.getRepository)
      walk.addTree(revCommit.getTree)
      val buffer = new scala.collection.mutable.ListBuffer[DiffInfo]()
      while(walk.next){
        buffer.append(DiffInfo(ChangeType.ADD, null, walk.getPathString, None, 
            JGitUtil.getContent(git, walk.getObjectId(0), false).map(new String(_, "UTF-8"))))
      }
      walk.release
      buffer.toList
    }
  }

}