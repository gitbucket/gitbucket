package gitbucket.core.api

case class ApiRepositoryCollaborator(
  permission: String,
  user: ApiUser
)
