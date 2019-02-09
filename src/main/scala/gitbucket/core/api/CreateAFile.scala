package gitbucket.core.api

/**
 * https://developer.github.com/v3/repos/contents/#create-a-file
 */
case class CreateAFile(
  message: String,
  content: String,
  sha: Option[String],
  branch: Option[String],
  committer: Option[ApiPusher],
  author: Option[ApiPusher]
)
