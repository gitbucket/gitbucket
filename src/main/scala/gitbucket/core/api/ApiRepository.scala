package gitbucket.core.api

import gitbucket.core.model.{Account, Repository}
import gitbucket.core.service.RepositoryService.RepositoryInfo


// https://developer.github.com/v3/repos/
case class ApiRepository(
  name: String,
  full_name: String,
  description: String,
  watchers: Int,
  forks: Int,
  `private`: Boolean,
  default_branch: String,
  owner: ApiUser) {
  val forks_count   = forks
  val watchers_count = watchers
  val url       = ApiPath(s"/api/v3/repos/${full_name}")
  val http_url  = ApiPath(s"/git/${full_name}.git")
  val clone_url = ApiPath(s"/git/${full_name}.git")
  val html_url  = ApiPath(s"/${full_name}")
}

object ApiRepository{
  def apply(
      repository: Repository,
      owner: ApiUser,
      forkedCount: Int =0,
      watchers: Int = 0): ApiRepository =
    ApiRepository(
      name        = repository.repositoryName,
      full_name   = s"${repository.userName}/${repository.repositoryName}",
      description = repository.description.getOrElse(""),
      watchers    = 0,
      forks       = forkedCount,
      `private`   = repository.isPrivate,
      default_branch = repository.defaultBranch,
      owner       = owner
    )

  def apply(repositoryInfo: RepositoryInfo, owner: ApiUser): ApiRepository =
    ApiRepository(repositoryInfo.repository, owner, forkedCount=repositoryInfo.forkedCount)

  def apply(repositoryInfo: RepositoryInfo, owner: Account): ApiRepository =
    this(repositoryInfo.repository, ApiUser(owner))

}
