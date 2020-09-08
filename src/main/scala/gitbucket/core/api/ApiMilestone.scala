package gitbucket.core.api

import gitbucket.core.model.{Milestone, Repository}
import gitbucket.core.util.RepositoryName
import java.util.Date

/**
 * https://docs.github.com/en/rest/reference/issues#milestones
 */
case class ApiMilestone(
  url: ApiPath,
//  html_url: ApiPath,
//  label_url: ApiPath,
  id: Int,
  number: Int,
  state: String,
  title: String,
  description: String,
  creator: ApiUser,
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
    user: ApiUser,
    milestone_number: Int,
    open_issue_count: Int = 0,
    closed_issue_count: Int = 0
  ): ApiMilestone =
    ApiMilestone(
      url = ApiPath(s"/api/v3/repos/${RepositoryName(repository).fullName}/milestones/${milestone_number}"),
//      html_url = ApiPath(s"/${RepositoryName(repository).fullName}/milestones/${milestone.title}"),
//      label_url = ApiPath(s"/api/v3/repos/${RepositoryName(repository).fullName}/milestones/${milestone_number}/labels"),
      id = milestone.milestoneId,
      number = milestone_number,
      state = if (milestone.closedDate.isDefined) "closed" else "open",
      title = milestone.title,
      description = milestone.description.getOrElse(""),
      creator = user,
      open_issues = open_issue_count,
      closed_issues = closed_issue_count,
      closed_at = milestone.closedDate,
      due_on = milestone.dueDate
    )
}
