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
   * Temporary directory which is used to create an archive to download repository contents.
   */
  def getDownloadWorkDir(owner: String, repository: String, sessionId: String): File = 
    new File("%s/tmp/%s/%s/download/%s".format(GitBucketHome, owner, repository, sessionId))
  
  /**
   * Temporary directory which is used in the repository creation.
   * GiyBucket generates initial repository contents in this directory and push them.
   * This directory is removed after the repository creation.
   */
  def getInitRepositoryDir(owner: String, repository: String): File =
    new File("%s/tmp/%s/init-%s".format(GitBucketHome, owner, repository))
  
  /**
   * Substance directory of the wiki repository.
   */
  def getWikiRepositoryDir(owner: String, repository: String): File =
    new File("%s/%s/%s.wiki.git".format(Directory.RepositoryHome, owner, repository))
  
  /**
   * Wiki working directory which is cloned from the wiki repository.
   */
  def getWikiWorkDir(owner: String, repository: String): File = 
    new File("%s/tmp/%s/%s.wiki".format(Directory.GitBucketHome, owner, repository))
  
}