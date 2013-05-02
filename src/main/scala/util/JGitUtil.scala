package util

import org.eclipse.jgit.api.Git
import app.{RepositoryInfo, FileInfo, CommitInfo, DiffInfo}
import util.Directory._
import scala.collection.JavaConverters._
import javax.servlet.ServletContext
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.treewalk.CanonicalTreeParser

/**
 * Provides complex JGit operations.
 */
object JGitUtil {
  
  /**
   * Returns the repository information. It contains branch names and tag names.
   */
  def getRepositoryInfo(owner: String, repository: String, servletContext: ServletContext): RepositoryInfo = {
    val git = Git.open(getRepositoryDir(owner, repository))
    RepositoryInfo(
      owner, repository, "http://localhost:8080%s/git/%s/%s.git".format(servletContext.getContextPath, owner, repository),
      // branches
      git.branchList.call.toArray.map { ref =>
        ref.asInstanceOf[Ref].getName.replaceFirst("^refs/heads/", "")
      }.toList,
      // tags
      git.tagList.call.toArray.map { ref =>
        ref.asInstanceOf[Ref].getName
      }.toList
    )   
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
   * @param limit the number of commit info per page. 0 means unlimited.
   * @return a tuple of the commit list and whether has next
   */
  def getCommitLog(git: Git, revision: String, page: Int = 1, limit: Int = 0): (List[CommitInfo], Boolean) = {
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
    
    val commits = getCommitLog(revWalk.iterator, 0, Nil)
    revWalk.release
    
    commits
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