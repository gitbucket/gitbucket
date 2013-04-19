package util

import java.io.File

/**
 * Provides directories used by GitBucket.
 */
object Directory {

  val GitBucketHome = new File(System.getProperty("user.home"), "gitbucket").getAbsolutePath
  
  /**
   * Repository names of the specified user.
   */
  def getRepositories(owner: String): List[String] = {
    val dir = new File("%s/repositories/%s".format(GitBucketHome, owner))
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
    new File("%s/repositories/%s/%s.git".format(GitBucketHome, owner, repository))
  
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

}