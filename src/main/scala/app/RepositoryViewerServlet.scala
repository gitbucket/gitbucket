package app

import util.Directory._
import util.Implicits._
import org.scalatra._
import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.errors.MissingObjectException
import org.apache.commons.io.FilenameUtils

case class RepositoryInfo(owner: String, name: String, url: String, branchList: List[String], tags: List[String])

case class FileInfo(isDirectory: Boolean, name: String, time: Date, message: String, committer: String)

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
    html.user(owner, getRepositories(owner).map(getRepositoryInfo(owner, _)))
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
  get("/:owner/:repository/tree/:branch") {
    val owner      = params("owner")
    val repository = params("repository")
    
    fileList(owner, repository, params("branch"))
  }
  
  /**
   * Displays the file list of the specified path and branch.
   */
  get("/:owner/:repository/tree/:branch/*") {
    val owner      = params("owner")
    val repository = params("repository")
    
    fileList(owner, repository, params("branch"), multiParams("splat").head)
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
    
    html.commits(branchName, getRepositoryInfo(owner, repository), 
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
    val repositoryInfo = getRepositoryInfo(owner, repository)
    
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
        val viewer  = if(isImage(file.getName)) "image" else if(isLarge(file.length)) "large" else "text"
        val content = ContentInfo(
            viewer, if(viewer == "text") Some(FileUtils.readFileToString(file, "UTF-8")) else None
        )
        html.blob(id, repositoryInfo, path.split("/").toList, content, new CommitInfo(rev))
      }
    } else {
      // id is commit id
      val branch = getBranchNameFromCommitId(id, repositoryInfo)
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
        getContent(git, objectId, false)
        
      } else {
        // Viewer
        val large   = isLarge(git.getRepository.getObjectDatabase.open(objectId).getSize)
        val viewer  = if(isImage(path)) "image" else if(large) "large" else "text"
        val content = ContentInfo(viewer, if(viewer == "text") getContent(git, objectId, false).map(new String(_, "UTF-8")) else None)
        
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
    
    val repositoryInfo = getRepositoryInfo(owner, repository)
    
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
            getContent(git, diff.getOldId.toObjectId, false).map(new String(_, "UTF-8")), 
            getContent(git, diff.getNewId.toObjectId, false).map(new String(_, "UTF-8")))
      }
    } else {
      // initial commit
      val walk = new TreeWalk(git.getRepository)
      walk.addTree(rev.getTree)
      val buffer = new scala.collection.mutable.ListBuffer[DiffInfo]()
      while(walk.next){
        buffer.append(DiffInfo(ChangeType.ADD, null, walk.getPathString, None, 
            getContent(git, walk.getObjectId(0), false).map(new String(_, "UTF-8"))))
      }
      buffer.toList
    }
    
    html.commit(branch, 
        CommitInfo(rev.getName, rev.getCommitterIdent.getWhen, rev.getCommitterIdent.getName, rev.getFullMessage), 
        repositoryInfo, diffs)
  }
  
  ///////////////////////////////////////////////////////////////////////////////////////////////
  //
  // TODO Helper methods should be separated to object?
  //
  //////////////////////////////////////////////////////////////////////////////////////////////
  
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
   * Get object content of the given id as String from the Git repository.
   * 
   * @param git the Git object
   * @param id the object id
   * @param large if true then returns None for the large file
   * @return the object or None if object does not exist
   */
  def getContent(git: Git, id: ObjectId, large: Boolean): Option[Array[Byte]] = try {
    val loader = git.getRepository.getObjectDatabase.open(id)
    if(large == false && isLarge(loader.getSize)){
      None
    } else {
      Some(git.getRepository.getObjectDatabase.open(id).getBytes)
    }
  } catch {
    case e: MissingObjectException => None
  }
  
  /**
   * Returns the repository information. It contains branch names and tag names.
   * 
   * @param owner the repository owner
   * @param repository the repository name
   */
  def getRepositoryInfo(owner: String, repository: String): RepositoryInfo = {
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
   * Provides HTML of the file list.
   * 
   * @param owner the repository owner
   * @param repository the repository name
   * @param branch the branch name (optional)
   * @param path the directory path (optional)
   * @return HTML of the file list
   */
  def fileList(owner: String, repository: String, branch: String = "", path: String = ".") = {
    val branchName = if(branch.isEmpty){
      Git.open(getRepositoryDir(owner, repository)).getRepository.getBranch
    } else {
      branch
    }
    
    val dir = getBranchDir(owner, repository, branchName)
    val git = Git.open(dir)
    val latestRev = {if(path == ".") git.log else git.log.addPath(path)}.call.iterator.next
    val files = new File(dir, path).listFiles()
        .filterNot{ file => file.getName == ".git" }
        .sortWith { (file1, file2) => (file1.isDirectory, file2.isDirectory) match {
          case (true , false) => true
          case (false, true ) => false
          case _ => file1.getName.compareTo(file2.getName) < 0
        }}
        .map { file =>
          val rev = Git.open(dir).log.addPath(if(path == ".") file.getName else path + "/" + file.getName).call.iterator.next
          if(rev == null){
            None
          } else {
            Some(FileInfo(file.isDirectory, file.getName, rev.getCommitterIdent.getWhen, rev.getShortMessage, rev.getCommitterIdent.getName))
          }
        }
        .flatten.toList
    
    // process README.md
    val readme = files.find(_.name == "README.md").map { file =>
      import org.pegdown._
      new PegDownProcessor().markdownToHtml(FileUtils.readFileToString(new File(dir, path + "/" + file.name), "UTF-8"))
    }
    
    html.files(
      // current branch
      branchName, 
      // repository
      getRepositoryInfo(owner, repository),
      // current path
      if(path == ".") Nil else path.split("/").toList,
      // latest commit
      CommitInfo(latestRev.getName, latestRev.getCommitterIdent.getWhen, latestRev.getCommitterIdent.getName, latestRev.getShortMessage),
      // file list
      files,
      // readme
      readme
    )
  }
  
  def isImage(name: String): Boolean = FilenameUtils.getExtension(name).toLowerCase match {
    case "jpg"|"jpeg"|"bmp"|"gif"|"png" => true
    case _ => false
  }
  
  def isLarge(size: Long): Boolean = (size > 1024 * 1000)
  
}