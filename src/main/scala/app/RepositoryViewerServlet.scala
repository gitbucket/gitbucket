package app

import util.Directory._
import util.Implicits._
import org.scalatra._
import java.io.File
import java.util.Date
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io.FileUtils

case class RepositoryInfo(owner: String, name: String, url: String, branchList: List[String], tags: List[String])

case class FileInfo(isDirectory: Boolean, name: String, time: Date, message: String, committer: String)

case class CommitInfo(id: String, time: Date, committer: String, message: String)

/**
 * The repository viewer.
 */
class RepositoryViewerServlet extends ScalatraServlet with ServletBase {
  
  get("/:owner") {
    val owner = params("owner")
    html.user.render(owner, getRepositories(owner).map(getRepositoryInfo(owner, _)))
  }
  
  /**
   * Shows the file list of the repository root and the default branch.
   */
  get("/:owner/:repository") {
    val owner = params("owner")
    val repository = params("repository")
    updateAllBranches(owner, repository)
    
    fileList(owner, repository)
  }
  
  /**
   * Shows the file list of the repository root and the specified branch.
   */
  get("/:owner/:repository/tree/:branch") {
    val owner = params("owner")
    val repository = params("repository")
    updateAllBranches(owner, repository)
    
    fileList(owner, repository, params("branch"))
  }
  
  /**
   * Shows the file list of the specified path and branch.
   */
  get("/:owner/:repository/tree/:branch/*") {
    val owner = params("owner")
    val repository = params("repository")
    updateAllBranches(owner, repository)
    
    fileList(owner, repository, params("branch"), multiParams("splat").head)
  }
  
  /**
   * Shows the commit list of the specified branch.
   */
  get("/:owner/:repository/commits/:branch"){
    val owner = params("owner")
    val repository = params("repository")
    updateAllBranches(owner, repository)
    
    val branchName = params("branch")
    val page = params.getOrElse("page", "1").toInt
    val dir = getBranchDir(owner, repository, branchName)
    
    val git = Git.open(dir)
    val i = git.log.call.iterator
    val listBuffer = scala.collection.mutable.ListBuffer[CommitInfo]()
    var count = 0
    while(i.hasNext && listBuffer.size < 30){
      count = count + 1
      val ref = i.next
      if((page - 1) * 30 < count){
        listBuffer.append(CommitInfo(ref.getName, ref.getCommitterIdent.getWhen, ref.getCommitterIdent.getName, ref.getShortMessage))
      }
    }
    
    html.commits.render(branchName, getRepositoryInfo(owner, repository), 
      listBuffer.toSeq.splitWith{ (commit1, commit2) =>
        view.helpers.date(commit1.time) == view.helpers.date(commit2.time)
      }, page, i.hasNext)
  }
  
  /**
   * Shows the file content of the specified branch.
   */
  get("/:owner/:repository/blob/:branch/*"){
    val owner = params("owner")
    val repository = params("repository")
    updateAllBranches(owner, repository)
    
    val branchName = params("branch")
    val path = multiParams("splat").head.replaceFirst("^tree/.+?/", "")
    
    val dir = getBranchDir(owner, repository, branchName)
    val content = FileUtils.readFileToString(new File(dir, path), "UTF-8")
    
    val git = Git.open(dir)
    val latestRev = git.log.addPath(path).call.iterator.next
    
    html.blob.render(branchName, getRepositoryInfo(owner, repository), path.split("/").toList, content,
      CommitInfo(latestRev.getName, latestRev.getCommitterIdent.getWhen, latestRev.getCommitterIdent.getName, latestRev.getShortMessage))
  }
  
  def getRepositoryInfo(owner: String, repository: String) = {
    val git = Git.open(getRepositoryDir(owner, repository))
    RepositoryInfo(
      owner, repository, "http://localhost:8080/git/%s/%s.git".format(owner, repository),
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
   * Setup all branches for the repository viewer.
   * This method copies the repository and checkout branches.
   */
  def updateAllBranches(owner: String, repository: String) = {
    val dir = getRepositoryDir(owner, repository)
    val git = Git.open(dir)
    
    val branchList = git.branchList.call.toArray.map { ref =>
      ref.asInstanceOf[Ref].getName
    }.toList
    
    branchList.foreach { branch =>
      val branchName = branch.replaceFirst("^refs/heads/", "")
      val branchdir = getBranchDir(owner, repository, branchName)
      if(!branchdir.exists){
        branchdir.mkdirs()
        Git.cloneRepository
          .setURI(dir.toURL.toString)
          .setBranch(branch)
          .setDirectory(branchdir)
          .call
        Git.open(branchdir).checkout.setName(branchName).call
      } else {
        Git.open(branchdir).pull.call
      }
    }
  }
  
  /**
   * Provides the file list of the specified branch and path.
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
    
    html.files.render(
      // current branch
      branchName, 
      // repository
      getRepositoryInfo(owner, repository),
      // current path
      if(path == ".") Nil else path.split("/").toList,
      // latest commit
      CommitInfo(latestRev.getName, latestRev.getCommitterIdent.getWhen, latestRev.getCommitterIdent.getName, latestRev.getShortMessage),
      // file list
      new File(dir, path).listFiles()
        .filterNot{ file => file.getName == ".git" }
        .sortWith { (file1, file2) => 
          if(file1.isDirectory && !file2.isDirectory){
            true
          } else if(!file1.isDirectory && file2.isDirectory){
            false
          } else {
            file1.getName.compareTo(file2.getName) < 0
          }
        }
        .map { file =>
          val rev = Git.open(dir).log.addPath(if(path == ".") file.getName else path + "/" + file.getName).call.iterator.next
          if(rev == null){
            None
          } else {
            Some(FileInfo(file.isDirectory, file.getName, latestRev.getCommitterIdent.getWhen, rev.getShortMessage, rev.getCommitterIdent.getName))
          }
        }
        .flatten.toList
    )
  }
  
}