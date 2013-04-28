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

case class RepositoryInfo(owner: String, name: String, url: String, branchList: List[String], tags: List[String])

case class FileInfo(id: ObjectId, isDirectory: Boolean, name: String, time: Date, message: String, committer: String)

case class CommitInfo(id: String, time: Date, committer: String, message: String){
  def this(rev: org.eclipse.jgit.revwalk.RevCommit) = 
    this(rev.getName, rev.getCommitterIdent.getWhen, rev.getCommitterIdent.getName, rev.getFullMessage)
}

case class DiffInfo(changeType: ChangeType, oldPath: String, newPath: String, oldContent: Option[String], newContent: Option[String])
  
case class ContentInfo(viewType: String, content: Option[String])

/**
 * The repository viewer.
 */
class RepositoryViewerServlet extends ServletBase {
  
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
    val dir        = getBranchDir(owner, repository, branchName)
    
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], count: Int, logs: List[CommitInfo]): (List[CommitInfo], Boolean)  =
      i.hasNext match {
        case true if(logs.size < 30) => getCommitLog(i, count + 1, if((page - 1) * 30 < count) logs :+ new CommitInfo(i.next) else logs)
        case _ => (logs, i.hasNext)
      }
    
    val (logs, hasNext) = getCommitLog(Git.open(dir).log.call.iterator, 0, Nil)
    
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
    
    if(repositoryInfo.branchList.contains(id)){
      // id is branch name
      val dir  = getBranchDir(owner, repository, id)
      val git  = Git.open(dir)
      val rev  = git.log.addPath(path).call.iterator.next
      val file = new File(dir, path)
      
      if(raw){
        // Download
        contentType = "application/octet-stream"
        file
      } else {
        // Viewer
        val viewer  = if(FileTypeUtil.isImage(file.getName)) "image" else if(FileTypeUtil.isLarge(file.length)) "large" else "text"
        val content = ContentInfo(
            viewer, if(viewer == "text") Some(FileUtils.readFileToString(file, "UTF-8")) else None
        )
        html.blob(id, repositoryInfo, path.split("/").toList, content, new CommitInfo(rev))
      }
    } else {
      // id is commit id
      val branch = JGitUtil.getBranchNameFromCommitId(id, repositoryInfo)
      val dir    = getBranchDir(owner, repository, branch)
      val git    = Git.open(dir)
      val rev    = git.log.add(ObjectId.fromString(id)).call.iterator.next
      
      @scala.annotation.tailrec
      def getPathObjectId(path: String, walk: TreeWalk): ObjectId = walk.next match {
        case true if(walk.getPathString == path) => walk.getObjectId(0)
        case true => getPathObjectId(path, walk)
      }
      
      val walk = new TreeWalk(git.getRepository)
      walk.addTree(rev.getTree)
      walk.setRecursive(true)
      val objectId = getPathObjectId(path, walk)
      
      if(raw){
        // Download
        contentType = "application/octet-stream"
        JGitUtil.getContent(git, objectId, false)
        
      } else {
        // Viewer
        val large   = FileTypeUtil.isLarge(git.getRepository.getObjectDatabase.open(objectId).getSize)
        val viewer  = if(FileTypeUtil.isImage(path)) "image" else if(large) "large" else "text"
        val content = ContentInfo(viewer, if(viewer == "text") JGitUtil.getContent(git, objectId, false).map(new String(_, "UTF-8")) else None)
        
        html.blob(branch, repositoryInfo, path.split("/").toList, content, new CommitInfo(rev))
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
    
    val repositoryInfo = JGitUtil.getRepositoryInfo(owner, repository, servletContext)
    
    // get branch by commit id
    val branch = repositoryInfo.branchList.find { branch =>
      val git = Git.open(getBranchDir(owner, repository, branch))
      git.log.add(ObjectId.fromString(id)).call.iterator.hasNext
    }.get
    
    val dir = getBranchDir(owner, repository, branch)
    val git = Git.open(dir)
    val ite = git.log.add(ObjectId.fromString(id)).call.iterator
    val rev = ite.next
    val old = ite.next
    
    val diffs = if(old != null){
      // get diff between specified commit and its previous commit
      val reader = git.getRepository.newObjectReader
      
      val oldTreeIter = new CanonicalTreeParser
      oldTreeIter.reset(reader, git.getRepository.resolve(old.name + "^{tree}"))
      
      val newTreeIter = new CanonicalTreeParser
      newTreeIter.reset(reader, git.getRepository.resolve(id + "^{tree}"))
      
      import scala.collection.JavaConverters._
      git.diff.setNewTree(newTreeIter).setOldTree(oldTreeIter).call.asScala.map { diff =>
        DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath,
            JGitUtil.getContent(git, diff.getOldId.toObjectId, false).map(new String(_, "UTF-8")), 
            JGitUtil.getContent(git, diff.getNewId.toObjectId, false).map(new String(_, "UTF-8")))
      }
    } else {
      // initial commit
      val walk = new TreeWalk(git.getRepository)
      walk.addTree(rev.getTree)
      val buffer = new scala.collection.mutable.ListBuffer[DiffInfo]()
      while(walk.next){
        buffer.append(DiffInfo(ChangeType.ADD, null, walk.getPathString, None, 
            JGitUtil.getContent(git, walk.getObjectId(0), false).map(new String(_, "UTF-8"))))
      }
      walk.release
      buffer.toList
    }
    
    html.commit(branch, 
        CommitInfo(rev.getName, rev.getCommitterIdent.getWhen, rev.getCommitterIdent.getName, rev.getFullMessage), 
        repositoryInfo, diffs)
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
    
    val files = JGitUtil.getFileList(owner, repository, revision, path)
    
    // process README.md
    val readme = files.find(_.name == "README.md").map { file =>
      import org.pegdown._
      val git = Git.open(getRepositoryDir(owner, repository))
      new PegDownProcessor().markdownToHtml(new String(git.getRepository.open(file.id).getBytes, "UTF-8"))
    }
    
    html.files(
      // current branch
      revision, 
      // repository
      JGitUtil.getRepositoryInfo(owner, repository, servletContext),
      // current path
      if(path == ".") Nil else path.split("/").toList,
      // latest commit
      CommitInfo(revCommit.getName, revCommit.getCommitterIdent.getWhen, revCommit.getCommitterIdent.getName, revCommit.getShortMessage),
      // file list
      files,
      // readme
      readme
    )
  }
  
}