package util

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref

/**
 * Provides directories used by GitBucket.
 */
object Directory {

  val GitBucketHome = new File(System.getProperty("user.home"), "gitbucket").getAbsolutePath

  val GitBucketConf = new File(GitBucketHome, "gitbucket.conf")
  
  val RepositoryHome = "%s/repositories".format(GitBucketHome)
  
  /**
   * Repository names of the specified user.
   */
  def getRepositories(owner: String): List[String] = {
    val dir = new File("%s/%s".format(RepositoryHome, owner))
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
    new File("%s/%s/%s.git".format(RepositoryHome, owner, repository))

  /**
   * Root of temporary directories for the specified repository.
   */
  def getTemporaryDir(owner: String, repository: String): File =
    new File("%s/tmp/%s/%s".format(GitBucketHome, owner, repository))

  /**
   * Temporary directory which is used to create an archive to download repository contents.
   */
  def getDownloadWorkDir(owner: String, repository: String, sessionId: String): File = 
    new File(getTemporaryDir(owner, repository), "download/%s".format(sessionId))
  
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
    new File("%s/%s/%s.wiki.git".format(Directory.RepositoryHome, owner, repository))
  
  /**
   * Wiki working directory which is cloned from the wiki repository.
   */
  def getWikiWorkDir(owner: String, repository: String): File =
    new File(getTemporaryDir(owner, repository), "wiki")

}