package gitbucket.core.api

case class UpdateAUser(
  name: String,
  email: String,
  blog: String,
  company: String,
  location: String,
  hireable: Boolean,
  bio: String
)
