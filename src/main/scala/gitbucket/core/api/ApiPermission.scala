package gitbucket.core.api
import gitbucket.core.service.RepositoryService

case class ApiPermission(
  admin: Boolean,
  push: Boolean,
  pull: Boolean
)

object ApiPermission {
  def apply(permission: RepositoryService.RepositoryPermission): ApiPermission =
    ApiPermission(permission.isOwner, permission.isDeveloper, permission.isGuest)
}
