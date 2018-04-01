package gitbucket.core.api

/**
 * https://developer.github.com/v3/issues/#create-an-issue
 */
case class CreateAnIssue(
  title: String,
  body: Option[String],
  assignees: List[String],
  milestone: Option[Int],
  labels: List[String]
)
