package util

import org.eclipse.jgit.api.Git
import app.{RepositoryInfo, FileInfo}
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
   * Get the branch name from the commit id.
   */
  def getBranchNameFromCommitId(id: String, repositoryInfo: RepositoryInfo): String = {
      repositoryInfo.branchList.find { branch =>
        val git = Git.open(getBranchDir(repositoryInfo.owner, repositoryInfo.name, branch))
        git.log.add(ObjectId.fromString(id)).call.iterator.hasNext
      }.get
  }
  
  /**
   * Returns the file list of the specified path.
   * 
   * @param owner the repository owner
   * @param repository the repository name
   * @param revstr the branch name or commit id
   * @param path the directory path (optional)
   * @return HTML of the file list
   */
  def getFileList(owner: String, repository: String, revision: String, path: String = "."): List[FileInfo] = {
    val git = Git.open(getRepositoryDir(owner, repository))
      
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
      val fileCommit = JGitUtil.getLatestCommitFromPath(git.getRepository, treeWalk.getPathString, revision)
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
   * Returns the latest RevCommit of the specified path.
   */
  def getLatestCommitFromPath(repository: Repository, path: String, revision: String): RevCommit = {
      val revWalk = new RevWalk(repository)
      revWalk.markStart(revWalk.parseCommit(repository.resolve(revision)))
      revWalk.sort(RevSort.REVERSE);
      val i = revWalk.iterator
      
      // TODO DON'T use var!
      var result: RevCommit = null
      
      while(i.hasNext){
        val commit = i.next
        if(commit.getParentCount == 0){
          // Initial commit
          val treeWalk = new TreeWalk(repository)
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
          df.setRepository(repository)
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
   * @param large if true then returns None for the large file
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

}