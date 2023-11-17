package gitbucket.core.api

import gitbucket.core.model.Issue
import gitbucket.core.util.RepositoryName

import java.util.Date

/**
 * https://developer.github.com/v3/issues/
 */
case class ApiIssue(
  number: Int,
  title: String,
  user: ApiUser,
  assignees: List[ApiUser],
  labels: List[ApiLabel],
  state: String,
  created_at: Date,
  updated_at: Date,
  body: String,
  milestone: Option[ApiMilestone]
)(repositoryName: RepositoryName, isPullRequest: Boolean) {
  val id = 0 // dummy id
  val assignee = assignees.headOption
  val comments_url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/issues/${number}/comments")
  val html_url = ApiPath(s"/${repositoryName.fullName}/${if (isPullRequest) { "pull" }
    else { "issues" }}/${number}")
  val pull_request = if (isPullRequest) {
    Some(
      Map(
        "url" -> ApiPath(s"/api/v3/repos/${repositoryName.fullName}/pulls/${number}"),
        "html_url" -> ApiPath(s"/${repositoryName.fullName}/pull/${number}")
        // "diff_url" -> ApiPath(s"/${repositoryName.fullName}/pull/${number}.diff"),
        // "patch_url" -> ApiPath(s"/${repositoryName.fullName}/pull/${number}.patch")
      )
    )
  } else {
    None
  }
}

object ApiIssue {
  def apply(
    issue: Issue,
    repositoryName: RepositoryName,
    user: ApiUser,
    assignees: List[ApiUser],
    labels: List[ApiLabel],
    milestone: Option[ApiMilestone]
  ): ApiIssue =
    ApiIssue(
      number = issue.issueId,
      title = issue.title,
      user = user,
      assignees = assignees,
      labels = labels,
      milestone = milestone,
      state = if (issue.closed) { "closed" }
      else { "open" },
      body = issue.content.getOrElse(""),
      created_at = issue.registeredDate,
      updated_at = issue.updatedDate
    )(repositoryName, issue.isPullRequest)
}
