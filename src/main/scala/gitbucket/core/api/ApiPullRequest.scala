package gitbucket.core.api

import gitbucket.core.model.{Account, Issue, IssueComment, PullRequest}
import java.util.Date
import gitbucket.core.service.AccountService
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._


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
  merged: Boolean,
  merged_at: Option[Date],
  merged_by: Option[ApiUser],
  title: String,
  body: String,
  user: ApiUser,
  assignee: Either[ApiUser,AnyRef]) extends AccountService {
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
  def apply(
    issue: Issue,
    pullRequest: PullRequest,
    headRepo: ApiRepository,
    baseRepo: ApiRepository,
    user: ApiUser,
    mergedComment: Option[(IssueComment, Account)]
  )(implicit s: Session): ApiPullRequest =
    ApiPullRequest(
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
      merged     = mergedComment.isDefined,
      merged_at  = mergedComment.map { case (comment, _) => comment.registeredDate },
      merged_by  = mergedComment.map { case (_, account) => ApiUser(account) },
      title      = issue.title,
      body       = issue.content.getOrElse(""),
      user       = user,
      assignee   = if (issue.assignedUserName == None) Right(null) else Left(ApiUser(getAccountByUserName(issue.assignedUserName.getOrElse("")).get))
    )

  case class Commit(
    sha: String,
    ref: String,
    repo: ApiRepository)(baseOwner:String){
    val label = if( baseOwner == repo.owner.login ){ ref }else{ s"${repo.owner.login}:${ref}" }
    val user = repo.owner
  }

  def getAccountByUserName(userName: String, includeRemoved: Boolean = false)(implicit s: Session): Option[Account] = 
    Accounts filter(t => (t.userName === userName.bind) && (t.removed === false.bind, !includeRemoved)) firstOption
}
