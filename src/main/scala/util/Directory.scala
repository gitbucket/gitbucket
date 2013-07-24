package util

import java.io.File

/**
 * Provides directories used by GitBucket.
 */
object Directory {

  val GitBucketHome = new File(System.getProperty("user.home"), "gitbucket").getAbsolutePath

  val GitBucketConf = new File(GitBucketHome, "gitbucket.conf")
  
  val RepositoryHome = s"${GitBucketHome}/repositories"
  
  /**
   * Repository names of the specified user.
   */
  def getRepositories(owner: String): List[String] = {
    val dir = new File(s"${RepositoryHome}/${owner}")
    if(dir.exists){
      dir.listFiles.filter { file =>
        file.isDirectory && !file.getName.endsWith(".wiki.git") 
      }.map(_.getName.replaceFirst("\\.git$", "")).toList
    } else {
      Nil
    }
  }
  
  /**
   * Substance directory of the repository.
   */
  def getRepositoryDir(owner: String, repository: String): File =
    new File(s"${RepositoryHome}/${owner}/${repository}.git")

  /**
   * Directory for uploaded files by the specified user.
   */
  def getUserUploadDir(userName: String): File = new File(s"${GitBucketHome}/data/${userName}/files")

  /**
   * Root of temporary directories for the specified repository.
   */
  def getTemporaryDir(owner: String, repository: String): File =
    new File(s"${GitBucketHome}/tmp/${owner}/${repository}")

  /**
   * Temporary directory which is used to create an archive to download repository contents.
   */
  def getDownloadWorkDir(owner: String, repository: String, sessionId: String): File = 
    new File(getTemporaryDir(owner, repository), s"download/${sessionId}")
  
  /**
   * Temporary directory which is used in the repository creation.
   *
   * GitBucket generates initial repository contents in this directory and push them.
   * This directory is removed after the repository creation.
   */
  def getInitRepositoryDir(owner: String, repository: String): File =
    new File(getTemporaryDir(owner, repository), "init")
  
  /**
   * Substance directory of the wiki repository.
   */
  def getWikiRepositoryDir(owner: String, repository: String): File =
    new File(s"${RepositoryHome}/${owner}/${repository}.wiki.git")
  
  /**
   * Wiki working directory which is cloned from the wiki repository.
   */
  def getWikiWorkDir(owner: String, repository: String): File =
    new File(getTemporaryDir(owner, repository), "wiki")

}