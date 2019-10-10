package gitbucket.core.api

import gitbucket.core.model.{Account, Issue, IssueComment, PullRequest}
import java.util.Date

/**
 * https://developer.github.com/v3/pulls/
 */
case class ApiPullRequest(
  number: Int,
  state: String,
  updated_at: Date,
  created_at: Date,
  head: ApiPullRequest.Commit,
  base: ApiPullRequest.Commit,
  mergeable: Option[Boolean],
  merged: Boolean,
  merged_at: Option[Date],
  merged_by: Option[ApiUser],
  title: String,
  body: String,
  user: ApiUser,
  labels: List[ApiLabel],
  assignee: Option[ApiUser],
  draft: Option[Boolean]
) {
  val id = 0 // dummy id
  val html_url = ApiPath(s"${base.repo.html_url.path}/pull/${number}")
  //val diff_url            = ApiPath(s"${base.repo.html_url.path}/pull/${number}.diff")
  //val patch_url           = ApiPath(s"${base.repo.html_url.path}/pull/${number}.patch")
  val url = ApiPath(s"${base.repo.url.path}/pulls/${number}")
  //val issue_url           = ApiPath(s"${base.repo.url.path}/issues/${number}")
  val commits_url = ApiPath(s"${base.repo.url.path}/pulls/${number}/commits")
  val review_comments_url = ApiPath(s"${base.repo.url.path}/pulls/${number}/comments")
  val review_comment_url = ApiPath(s"${base.repo.url.path}/pulls/comments/{number}")
  val comments_url = ApiPath(s"${base.repo.url.path}/issues/${number}/comments")
  val statuses_url = ApiPath(s"${base.repo.url.path}/statuses/${head.sha}")
}

object ApiPullRequest {
  def apply(
    issue: Issue,
    pullRequest: PullRequest,
    headRepo: ApiRepository,
    baseRepo: ApiRepository,
    user: ApiUser,
    labels: List[ApiLabel],
    assignee: Option[ApiUser],
    mergedComment: Option[(IssueComment, Account)]
  ): ApiPullRequest =
    ApiPullRequest(
      number = issue.issueId,
      state = if (issue.closed) "closed" else "open",
      updated_at = issue.updatedDate,
      created_at = issue.registeredDate,
      head = Commit(sha = pullRequest.commitIdTo, ref = pullRequest.requestBranch, repo = headRepo)(issue.userName),
      base = Commit(sha = pullRequest.commitIdFrom, ref = pullRequest.branch, repo = baseRepo)(issue.userName),
      mergeable = None, // TODO: need check mergeable.
      merged = mergedComment.isDefined,
      merged_at = mergedComment.map { case (comment, _) => comment.registeredDate },
      merged_by = mergedComment.map { case (_, account) => ApiUser(account) },
      title = issue.title,
      body = issue.content.getOrElse(""),
      user = user,
      labels = labels,
      assignee = assignee,
      draft = Some(pullRequest.isDraft)
    )

  case class Commit(sha: String, ref: String, repo: ApiRepository)(baseOwner: String) {
    val label = if (baseOwner == repo.owner.login) { ref } else { s"${repo.owner.login}:${ref}" }
    val user = repo.owner
  }

}
