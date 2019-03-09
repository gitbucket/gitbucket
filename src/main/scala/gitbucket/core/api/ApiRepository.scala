package gitbucket.core.api

import java.time.Instant

import gitbucket.core.model.{Account, Repository}
import gitbucket.core.service.RepositoryService
import gitbucket.core.service.RepositoryService.RepositoryInfo

// https://developer.github.com/v3/repos/
case class ApiRepository(
  name: String,
  full_name: String,
  description: String,
  id: Int,
  watchers: Int,
  forks: Int,
  `private`: Boolean,
  fork: Boolean,
  default_branch: String,
  owner: ApiUser,
  created_at: java.util.Date,
  updated_at: java.util.Date,
  permissions: Option[ApiPermission]
) {
  val forks_count = forks
  val watchers_count = watchers
  val url = ApiPath(s"/api/v3/repos/${full_name}")
  val http_url = ApiPath(s"/git/${full_name}.git")
  val clone_url = ApiPath(s"/git/${full_name}.git")
  val html_url = ApiPath(s"/${full_name}")
  val ssh_url = Some(SshPath(s":${full_name}.git"))
}

object ApiRepository {
  def apply(
    repository: Repository,
    owner: ApiUser,
    permission: Option[RepositoryService.RepositoryPermission],
    forkedCount: Int = 0,
    watchers: Int = 0
  ): ApiRepository =
    ApiRepository(
      name = repository.repositoryName,
      full_name = s"${repository.userName}/${repository.repositoryName}",
      description = repository.description.getOrElse(""),
      id = repository.repositoryId,
      watchers = watchers,
      forks = forkedCount,
      `private` = repository.isPrivate,
      fork = repository.parentRepositoryName.isDefined,
      default_branch = repository.defaultBranch,
      owner = owner,
      created_at = repository.registeredDate,
      updated_at = repository.updatedDate,
      permission.map(ApiPermission(_))
    )

  def apply(
    repositoryInfo: RepositoryInfo,
    owner: Account,
    permission: RepositoryService.RepositoryPermission
  ): ApiRepository =
    ApiRepository(repositoryInfo.repository, ApiUser(owner), Some(permission), forkedCount = repositoryInfo.forkedCount)

  def apply(
    repositoryInfo: RepositoryInfo,
    owner: Account
  ): ApiRepository =
    ApiRepository(repositoryInfo.repository, ApiUser(owner), None, forkedCount = repositoryInfo.forkedCount)

  def forDummyPayload(owner: ApiUser): ApiRepository =
    ApiRepository(
      name = "dummy",
      full_name = s"${owner.login}/dummy",
      description = "",
      id = 0,
      watchers = 0,
      forks = 0,
      `private` = false,
      fork = false,
      default_branch = "master",
      owner = owner,
      new java.util.Date,
      new java.util.Date,
      Some(ApiPermission(RepositoryService.RepositoryPermission(true, true, true)))
    )
}
