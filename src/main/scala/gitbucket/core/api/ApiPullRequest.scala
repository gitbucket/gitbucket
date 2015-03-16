package gitbucket.core.api

import gitbucket.core.model.{Issue, PullRequest}

import java.util.Date


/**
 * https://developer.github.com/v3/pulls/
 */
case class ApiPullRequest(
  number: Int,
  updated_at: Date,
  created_at: Date,
  head: ApiPullRequest.Commit,
  base: ApiPullRequest.Commit,
  mergeable: Option[Boolean],
  title: String,
  body: String,
  user: ApiUser) {
  val html_url            = ApiPath(s"${base.repo.html_url.path}/pull/${number}")
  //val diff_url            = ApiPath(s"${base.repo.html_url.path}/pull/${number}.diff")
  //val patch_url           = ApiPath(s"${base.repo.html_url.path}/pull/${number}.patch")
  val url                 = ApiPath(s"${base.repo.url.path}/pulls/${number}")
  //val issue_url           = ApiPath(s"${base.repo.url.path}/issues/${number}")
  val commits_url         = ApiPath(s"${base.repo.url.path}/pulls/${number}/commits")
  val review_comments_url = ApiPath(s"${base.repo.url.path}/pulls/${number}/comments")
  val review_comment_url  = ApiPath(s"${base.repo.url.path}/pulls/comments/{number}")
  val comments_url        = ApiPath(s"${base.repo.url.path}/issues/${number}/comments")
  val statuses_url        = ApiPath(s"${base.repo.url.path}/statuses/${head.sha}")
}

object ApiPullRequest{
  def apply(issue: Issue, pullRequest: PullRequest, headRepo: ApiRepository, baseRepo: ApiRepository, user: ApiUser): ApiPullRequest = ApiPullRequest(
      number     = issue.issueId,
      updated_at = issue.updatedDate,
      created_at = issue.registeredDate,
      head       = Commit(
                     sha  = pullRequest.commitIdTo,
                     ref  = pullRequest.requestBranch,
                     repo = headRepo)(issue.userName),
      base       = Commit(
                     sha  = pullRequest.commitIdFrom,
                     ref  = pullRequest.branch,
                     repo = baseRepo)(issue.userName),
      mergeable  = None, // TODO: need check mergeable.
      title      = issue.title,
      body       = issue.content.getOrElse(""),
      user       = user
    )

  case class Commit(
    sha: String,
    ref: String,
    repo: ApiRepository)(baseOwner:String){
    val label = if( baseOwner == repo.owner.login ){ ref }else{ s"${repo.owner.login}:${ref}" }
    val user = repo.owner
  }
}
