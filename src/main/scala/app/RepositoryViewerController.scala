package app

import util.Directory._
import util.Implicits._
import util.{JGitUtil, FileTypeUtil}
import org.scalatra._
import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.revwalk.RevWalk

// TODO Should models move to other package?
/**
 * The repository data.
 * 
 * @param owner the user name of the repository owner
 * @param repository the repository name
 * @param url the repository URL
 * @param branchList the list of branch names
 * @param tags the list of tags
 */
case class RepositoryInfo(owner: String, name: String, url: String, branchList: List[String], tags: List[String])

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
 * The repository viewer.
 */
class RepositoryViewerController extends ControllerBase {
  
  /**
   * Displays user information.
   */
  get("/:owner") {
    val owner = params("owner")
    
    html.user(owner, getRepositories(owner).map(JGitUtil.getRepositoryInfo(owner, _, servletContext)))
  }
  
  /**
   * Displays the file list of the repository root and the default branch.
   */
  get("/:owner/:repository") {
    val owner      = params("owner")
    val repository = params("repository")
    
    fileList(owner, repository)
  }
  
  /**
   * Displays the file list of the repository root and the specified branch.
   */
  get("/:owner/:repository/tree/:id") {
    val owner      = params("owner")
    val repository = params("repository")
    
    fileList(owner, repository, params("id"))
  }
  
  /**
   * Displays the file list of the specified path and branch.
   */
  get("/:owner/:repository/tree/:id/*") {
    val owner      = params("owner")
    val repository = params("repository")
    
    fileList(owner, repository, params("id"), multiParams("splat").head)
  }
  
  /**
   * Displays the commit list of the specified branch.
   */
  get("/:owner/:repository/commits/:branch"){
    val owner      = params("owner")
    val repository = params("repository")
    val branchName = params("branch")
    val page       = params.getOrElse("page", "1").toInt
    
    val (logs, hasNext) = JGitUtil.getCommitLog(Git.open(getRepositoryDir(owner, repository)), branchName, page)
    
    html.commits(branchName, JGitUtil.getRepositoryInfo(owner, repository, servletContext), 
      logs.splitWith{ (commit1, commit2) =>
        view.helpers.date(commit1.time) == view.helpers.date(commit2.time)
      }, page, hasNext)
  }
  
  /**
   * Displays the file content of the specified branch or commit.
   */
  get("/:owner/:repository/blob/:id/*"){
    val owner      = params("owner")
    val repository = params("repository")
    val id         = params("id") // branch name or commit id
    val raw        = params.get("raw").getOrElse("false").toBoolean
    val path       = multiParams("splat").head.replaceFirst("^tree/.+?/", "")
    val repositoryInfo = JGitUtil.getRepositoryInfo(owner, repository, servletContext)

    val git = Git.open(getRepositoryDir(owner, repository))
    val commitId = git.getRepository.resolve(id)
      
    val revWalk = new RevWalk(git.getRepository)
    val revCommit = revWalk.parseCommit(commitId)
    revWalk.dispose
      
    @scala.annotation.tailrec
    def getPathObjectId(path: String, walk: TreeWalk): ObjectId = walk.next match {
      case true if(walk.getPathString == path) => walk.getObjectId(0)
      case true => getPathObjectId(path, walk)
    }
      
    val treeWalk = new TreeWalk(git.getRepository)
    treeWalk.addTree(revCommit.getTree)
    treeWalk.setRecursive(true)
    val objectId = getPathObjectId(path, treeWalk)
    treeWalk.release
      
    if(raw){
      // Download
      contentType = "application/octet-stream"
      JGitUtil.getContent(git, objectId, false)
        
    } else {
      // Viewer
      val large   = FileTypeUtil.isLarge(git.getRepository.getObjectDatabase.open(objectId).getSize)
      val viewer  = if(FileTypeUtil.isImage(path)) "image" else if(large) "large" else "text"
      val content = ContentInfo(viewer, if(viewer == "text") JGitUtil.getContent(git, objectId, false).map(new String(_, "UTF-8")) else None)
        
      html.blob(id, repositoryInfo, path.split("/").toList, content, new CommitInfo(revCommit))
    }
  }
  
  /**
   * Displays details of the specified commit.
   */
  get("/:owner/:repository/commit/:id"){
    val owner      = params("owner")
    val repository = params("repository")
    val id         = params("id")
    
    val git = Git.open(getRepositoryDir(owner, repository))
    
    val revWalk = new RevWalk(git.getRepository)
    val objectId = git.getRepository.resolve(id)
    val revCommit = revWalk.parseCommit(objectId)
    revWalk.dispose
    
    html.commit(id, new CommitInfo(revCommit), JGitUtil.getRepositoryInfo(owner, repository, servletContext), JGitUtil.getDiffs(git, id))
  }
  
  /**
   * Provides HTML of the file list.
   * 
   * @param owner the repository owner
   * @param repository the repository name
   * @param rev the branch name or commit id(optional)
   * @param path the directory path (optional)
   * @return HTML of the file list
   */
  private def fileList(owner: String, repository: String, revstr: String = "", path: String = ".") = {
    val revision = if(revstr.isEmpty){
      Git.open(getRepositoryDir(owner, repository)).getRepository.getBranch
    } else {
      revstr
    }
      
    val git = Git.open(getRepositoryDir(owner, repository))

    // get latest commit
    val revWalk = new RevWalk(git.getRepository)
    val objectId = git.getRepository.resolve(revision)
    val revCommit = revWalk.parseCommit(objectId)
    revWalk.dispose
    
    val files = JGitUtil.getFileList(git, revision, path)
    
    // process README.md
    val readme = files.find(_.name == "README.md").map { file =>
      new String(JGitUtil.getContent(Git.open(getRepositoryDir(owner, repository)), file.id, true).get, "UTF-8")
    }
    
    html.files(
      // current branch
      revision, 
      // repository
      JGitUtil.getRepositoryInfo(owner, repository, servletContext),
      // current path
      if(path == ".") Nil else path.split("/").toList,
      // latest commit
      new CommitInfo(revCommit),
      // file list
      files,
      // readme
      readme
    )
  }
  
}