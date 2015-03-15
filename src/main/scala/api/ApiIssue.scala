package api

import java.util.Date
import model.Issue

/**
 * https://developer.github.com/v3/issues/
 */
case class ApiIssue(
  number: Int,
  title: String,
  user: ApiUser,
  // labels,
  state: String,
  created_at: Date,
  updated_at: Date,
  body: String)

object ApiIssue{
  def apply(issue: Issue, user: ApiUser): ApiIssue =
    ApiIssue(
      number = issue.issueId,
      title  = issue.title,
      user   = user,
      state  = if(issue.closed){ "closed" }else{ "open" },
      body   = issue.content.getOrElse(""),
      created_at = issue.registeredDate,
      updated_at = issue.updatedDate)
}
