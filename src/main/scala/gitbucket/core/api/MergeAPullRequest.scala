package gitbucket.core.api

/**
 * https://docs.github.com/en/rest/reference/pulls#merge-a-pull-request
 */
case class MergeAPullRequest(
  commit_title: Option[String],
  commit_message: Option[String],
  /* TODO: Not Implemented
  sha: Option[String],*/
  merge_method: Option[String]
)

case class SuccessToMergePrResponse(
  sha: String,
  merged: Boolean,
  message: String
)

case class FailToMergePrResponse(
  documentation_url: String,
  message: String
)
