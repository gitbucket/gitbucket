package gitbucket.core.api

import gitbucket.core.model.CommitState

/**
 * https://developer.github.com/v3/repos/statuses/#create-a-status
 * api form
 */
case class CreateAStatus(
  /* state is Required. The state of the status. Can be one of pending, success, error, or failure. */
  state: String,
  /* context is a string label to differentiate this status from the status of other systems. Default: "default" */
  context: Option[String],
  /* The target URL to associate with this status. This URL will be linked from the GitHub UI to allow users to easily see the ‘source’ of the Status. */
  target_url: Option[String],
  /* description is a short description of the status.*/
  description: Option[String]
) {
  def isValid: Boolean = {
    CommitState.valueOf(state).isDefined &&
      // only http
      target_url.filterNot(f => "\\Ahttps?://".r.findPrefixOf(f).isDefined && f.length<255).isEmpty &&
      context.filterNot(f => f.length<255).isEmpty &&
      description.filterNot(f => f.length<1000).isEmpty
  }
}
