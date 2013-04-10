package util

import java.io.File

object Directory {

  val GitBucketHome = "C:/Users/takez_000/gitbucket"
    
  def getRepositories(owner: String): List[String] =
    new File("%s/%s".format(GitBucketHome, owner)).listFiles.filter(_.isDirectory).map(_.getName).toList
    
  def getRepositoryDir(owner: String, repository: String): File =
    new File("%s/%s/%s/repository".format(GitBucketHome, owner, repository))
  
  def getBranchDir(owner: String, repository: String, branch: String): File =
    new File("%s/%s/%s/branch/%s".format(GitBucketHome, owner, repository, branch))
  
  def getTagDir(owner: String, repository: String, tag: String): File =
    new File("%s/%s/%s/tags/%s".format(GitBucketHome, owner, repository, tag))
  
}