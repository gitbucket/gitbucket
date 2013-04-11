package util

import java.io.File

object Directory {

  val GitBucketHome = "C:/Users/takezoe/gitbucket/"
    
  def getRepositories(owner: String): List[String] =
    new File("%s/repositories/%s".format(GitBucketHome, owner))
      .listFiles.filter(_.isDirectory).map(_.getName.replaceFirst("\\.git$", "")).toList
    
  def getRepositoryDir(owner: String, repository: String): File =
    new File("%s/repositories/%s/%s.git".format(GitBucketHome, owner, repository))
  
  def getBranchDir(owner: String, repository: String, branch: String): File =
    new File("%s/tmp/%s/branches/%s/%s".format(GitBucketHome, owner, repository, branch))
  
  def getInitRepositoryDir(owner: String, repository: String): File =
    new File("%s/tmp/%s/init-%s".format(GitBucketHome, owner, repository))

}