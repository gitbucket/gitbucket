package app

import util.Directory._
import util.Implicits._
import util.{JGitUtil, FileTypeUtil, CompressUtil}
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
 * The repository viewer.
 */
class RepositoryViewerController extends ControllerBase {
  
  // TODO separate to AccountController?
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
    
    JGitUtil.withGit(getRepositoryDir(owner, repository)){ git =>
      val (logs, hasNext) = JGitUtil.getCommitLog(git, branchName, page, 30)
    
      repo.html.commits(Nil, branchName, JGitUtil.getRepositoryInfo(owner, repository, servletContext), 
        logs.splitWith{ (commit1, commit2) =>
          view.helpers.date(commit1.time) == view.helpers.date(commit2.time)
        }, page, hasNext)
    }
  }
  
  /**
   * Displays the commit list of the specified resource.
   */
  get("/:owner/:repository/commits/:branch/*"){
    val owner      = params("owner")
    val repository = params("repository")
    val branchName = params("branch")
    val path       = multiParams("splat").head //.replaceFirst("^tree/.+?/", "")
    val page       = params.getOrElse("page", "1").toInt
    
    JGitUtil.withGit(getRepositoryDir(owner, repository)){ git =>
      val (logs, hasNext) = JGitUtil.getCommitLog(git, branchName, page, 30, path)
    
      repo.html.commits(path.split("/").toList, branchName, JGitUtil.getRepositoryInfo(owner, repository, servletContext), 
        logs.splitWith{ (commit1, commit2) =>
          view.helpers.date(commit1.time) == view.helpers.date(commit2.time)
        }, page, hasNext)
    }
  }
  
  
  /**
   * Displays the file content of the specified branch or commit.
   */
  get("/:owner/:repository/blob/:id/*"){
    val owner      = params("owner")
    val repository = params("repository")
    val id         = params("id") // branch name or commit id
    val raw        = params.get("raw").getOrElse("false").toBoolean
    val path       = multiParams("splat").head //.replaceFirst("^tree/.+?/", "")
    val repositoryInfo = JGitUtil.getRepositoryInfo(owner, repository, servletContext)

    JGitUtil.withGit(getRepositoryDir(owner, repository)){ git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))
      
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
        JGitUtil.getContent(git, objectId, false).get
      } else {
        // Viewer
        val large   = FileTypeUtil.isLarge(git.getRepository.getObjectDatabase.open(objectId).getSize)
        val viewer  = if(FileTypeUtil.isImage(path)) "image" else if(large) "large" else "text"
        val content = ContentInfo(viewer, 
            if(viewer == "text") JGitUtil.getContent(git, objectId, false).map(new String(_, "UTF-8")) else None)
        
        repo.html.blob(id, repositoryInfo, path.split("/").toList, content, new CommitInfo(revCommit))
      }
    }
  }
  
  /**
   * Displays details of the specified commit.
   */
  get("/:owner/:repository/commit/:id"){
    val owner      = params("owner")
    val repository = params("repository")
    val id         = params("id")
    
    JGitUtil.withGit(getRepositoryDir(owner, repository)){ git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))
      repo.html.commit(id, new CommitInfo(revCommit), 
          JGitUtil.getRepositoryInfo(owner, repository, servletContext), JGitUtil.getDiffs(git, id))
    }
  }
  
  /**
   * Displays tags.
   */
  get("/:owner/:repository/tags"){
    val owner      = params("owner")
    val repository = params("repository")
    
    repo.html.tags(JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }
  
  /**
   * Download repository contents as an archive.
   */
  get("/:owner/:repository/archive/:name"){
    val owner      = params("owner")
    val repository = params("repository")
    val name       = params("name")
    
    if(name.endsWith(".zip")){
      val revision = name.replaceFirst("\\.zip$", "")
      val workDir = getDownloadWorkDir(owner, repository, session.getId)
      if(workDir.exists){
        FileUtils.deleteDirectory(workDir)
      }
      workDir.mkdirs
      
      // clone the repository
      val cloneDir = new File(workDir, revision)
      JGitUtil.withGit(Git.cloneRepository
          .setURI(getRepositoryDir(owner, repository).toURI.toString)
          .setDirectory(cloneDir)
          .call){ git =>
      
        // checkout the specified revision
        git.checkout.setName(revision).call
      }
      
      // remove .git
      FileUtils.deleteDirectory(new File(cloneDir, ".git"))
      
      // create zip file
      val zipFile = new File(workDir, (if(revision.length == 40) revision.substring(0, 10) else revision) + ".zip")
      CompressUtil.zip(zipFile, cloneDir)
      
      contentType = "application/octet-stream"
      zipFile
    } else {
      BadRequest
    }
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
      
    JGitUtil.withGit(getRepositoryDir(owner, repository)){ git =>
      // get latest commit
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(revision))
    
      val files = JGitUtil.getFileList(git, revision, path)
    
      // process README.md
      val readme = files.find(_.name == "README.md").map { file =>
        new String(JGitUtil.getContent(Git.open(getRepositoryDir(owner, repository)), file.id, true).get, "UTF-8")
      }
    
      repo.html.files(
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
  
}