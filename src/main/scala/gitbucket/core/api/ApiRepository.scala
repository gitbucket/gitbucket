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
  owner: ApiUser)(urlIsHtmlUrl: Boolean) {
  val forks_count = forks
  val watchers_count = watchers
  val url = if(urlIsHtmlUrl){
    ApiPath(s"/${full_name}")
  } else {
    ApiPath(s"/api/v3/repos/${full_name}")
  }
  val http_url  = ApiPath(s"/git/${full_name}.git")
  val clone_url = ApiPath(s"/git/${full_name}.git")
  val html_url  = ApiPath(s"/${full_name}")
  val ssh_url   = Some(SshPath(s":${full_name}.git"))
}

object ApiRepository{
  def apply(
      repository: Repository,
      owner: ApiUser,
      forkedCount: Int =0,
      watchers: Int = 0,
      urlIsHtmlUrl: Boolean = false): ApiRepository =
    ApiRepository(
      name           = repository.repositoryName,
      full_name      = s"${repository.userName}/${repository.repositoryName}",
      description    = repository.description.getOrElse(""),
      watchers       = watchers,
      forks          = forkedCount,
      `private`      = repository.isPrivate,
      default_branch = repository.defaultBranch,
      owner          = owner
    )(urlIsHtmlUrl)

  def apply(repositoryInfo: RepositoryInfo, owner: ApiUser): ApiRepository =
    ApiRepository(repositoryInfo.repository, owner, forkedCount=repositoryInfo.forkedCount)

  def apply(repositoryInfo: RepositoryInfo, owner: Account): ApiRepository =
    this(repositoryInfo.repository, ApiUser(owner))

  def forPushPayload(repositoryInfo: RepositoryInfo, owner: ApiUser): ApiRepository =
    ApiRepository(repositoryInfo.repository, owner, forkedCount=repositoryInfo.forkedCount, urlIsHtmlUrl=true)

  def forDummyPayload(owner: ApiUser): ApiRepository =
    ApiRepository(
      name           = "dummy",
      full_name      = s"${owner.login}/dummy",
      description    = "",
      watchers       = 0,
      forks          = 0,
      `private`      = false,
      default_branch = "master",
      owner          = owner
    )(true)
}
