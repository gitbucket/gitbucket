package gitbucket.core.api

import gitbucket.core.model.{Milestone, Repository}
import gitbucket.core.util.RepositoryName
import java.util.Date

/**
 * https://docs.github.com/en/rest/reference/issues#milestones
 */
case class ApiMilestone(
  url: ApiPath,
  html_url: ApiPath,
//  label_url: ApiPath,
  id: Int,
  number: Int,
  state: String,
  title: String,
  description: String,
//  creator: ApiUser,  // MILESTONE table does not have created user column
  open_issues: Int,
  closed_issues: Int,
//  created_at: Option[Date],
//  updated_at: Option[Date],
  closed_at: Option[Date],
  due_on: Option[Date]
)

object ApiMilestone {
  def apply(
    repository: Repository,
    milestone: Milestone,
    open_issue_count: Int = 0,
    closed_issue_count: Int = 0
  ): ApiMilestone =
    ApiMilestone(
      url = ApiPath(s"/api/v3/repos/${RepositoryName(repository).fullName}/milestones/${milestone.milestoneId}"),
      html_url = ApiPath(s"/${RepositoryName(repository).fullName}/milestone/${milestone.milestoneId}"),
//      label_url = ApiPath(s"/api/v3/repos/${RepositoryName(repository).fullName}/milestones/${milestone_number}/labels"),
      id = milestone.milestoneId,
      number = milestone.milestoneId, // use milestoneId as number
      state = if (milestone.closedDate.isDefined) "closed" else "open",
      title = milestone.title,
      description = milestone.description.getOrElse(""),
      open_issues = open_issue_count,
      closed_issues = closed_issue_count,
      closed_at = milestone.closedDate,
      due_on = milestone.dueDate
    )
}
