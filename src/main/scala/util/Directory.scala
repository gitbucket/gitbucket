package util

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref

/**
 * Provides directories used by GitBucket.
 */
object Directory {

  val GitBucketHome = new File(System.getProperty("user.home"), "gitbucket").getAbsolutePath
  
  val RepositoryHome = "%s/repositories".format(GitBucketHome)
  
  /**
   * Repository names of the specified user.
   */
  def getRepositories(owner: String): List[String] = {
    val dir = new File("%s/%s".format(RepositoryHome, owner))
    if(dir.exists){
      dir.listFiles.filter(_.isDirectory).map(_.getName.replaceFirst("\\.git$", "")).toList
    } else {
      Nil
    }
  }
  
  /**
   * Substance directory of the repository.
   */
  def getRepositoryDir(owner: String, repository: String): File =
    new File("%s/%s/%s.git".format(RepositoryHome, owner, repository))
  
  /**
   * Temporary directory which is used in the repository viewer.
   */
  def getBranchDir(owner: String, repository: String, branch: String): File =
    new File("%s/tmp/%s/branches/%s/%s".format(GitBucketHome, owner, repository, branch))
  
  /**
   * Temporary directory which is used in the repository creation.
   * GiyBucket generates initial repository contents in this directory and push them.
   * This directory is removed after the repository creation.
   */
  def getInitRepositoryDir(owner: String, repository: String): File =
    new File("%s/tmp/%s/init-%s".format(GitBucketHome, owner, repository))

  def updateAllBranches(owner: String, repository: String): Unit = {
    // TODO debug log
    println("[pull]" + owner + "/" + repository)
    
    val dir = Directory.getRepositoryDir(owner, repository)
    val git = Git.open(dir)
    
    val branchList = git.branchList.call.toArray.map { ref =>
      ref.asInstanceOf[Ref].getName
    }.toList
    
    branchList.foreach { branch =>
      val branchName = branch.replaceFirst("^refs/heads/", "")
      val branchdir = Directory.getBranchDir(owner, repository, branchName)
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

  
}