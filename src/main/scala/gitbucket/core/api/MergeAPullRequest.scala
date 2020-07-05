package gitbucket.core.api

/**
 * https://developer.github.com/v3/pulls/#merge-a-pull-request
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
  documentation_ur: String,
  message: String
)
