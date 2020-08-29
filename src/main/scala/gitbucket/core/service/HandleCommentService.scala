package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.Issue
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.activity.{
  CloseIssueInfo,
  ClosePullRequestInfo,
  IssueCommentInfo,
  PullRequestCommentInfo,
  ReopenIssueInfo,
  ReopenPullRequestInfo
}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Implicits._

trait HandleCommentService {
  self: RepositoryService
    with IssuesService
    with ActivityService
    with WebHookService
    with WebHookIssueCommentService
    with WebHookPullRequestService =>

  /**
   * @see [[https://github.com/gitbucket/gitbucket/wiki/CommentAction]]
   */
  def handleComment(
    issue: Issue,
    content: Option[String],
    repository: RepositoryService.RepositoryInfo,
    actionOpt: Option[String]
  )(implicit context: Context, s: Session) = {
    context.loginAccount.flatMap { loginAccount =>
      defining(repository.owner, repository.name) {
        case (owner, name) =>
          val userName = loginAccount.userName

          actionOpt.collect {
            case "close" if !issue.closed =>
              updateClosed(owner, name, issue.issueId, true)
            case "reopen" if issue.closed =>
              updateClosed(owner, name, issue.issueId, false)
          }

          val (action, _) = actionOpt
            .collect {
              case "close" if !issue.closed =>
                val info = if (issue.isPullRequest) {
                  ClosePullRequestInfo(owner, name, userName, issue.issueId, issue.title)
                } else {
                  CloseIssueInfo(owner, name, userName, issue.issueId, issue.title)
                }
                recordActivity(info)
                Some("close") -> info
              case "reopen" if issue.closed =>
                val info = if (issue.isPullRequest) {
                  ReopenPullRequestInfo(owner, name, userName, issue.issueId, issue.title)
                } else {
                  ReopenIssueInfo(owner, name, userName, issue.issueId, issue.title)
                }
                recordActivity(info)
                Some("reopen") -> info
            }
            .getOrElse(None -> None)

          val commentId = (content, action) match {
            case (None, None) => None
            case (None, Some(action)) =>
              Some(createComment(owner, name, userName, issue.issueId, action.capitalize, action))
            case (Some(content), _) =>
              val id = Some(
                createComment(
                  owner,
                  name,
                  userName,
                  issue.issueId,
                  content,
                  action.map(_ + "_comment").getOrElse("comment")
                )
              )

              // record comment activity
              val commentInfo = if (issue.isPullRequest) {
                PullRequestCommentInfo(owner, name, userName, content, issue.issueId)
              } else {
                IssueCommentInfo(owner, name, userName, content, issue.issueId)
              }
              recordActivity(commentInfo)

              // extract references and create refer comment
              createReferComment(owner, name, issue, content, loginAccount)

              id
          }

          // call web hooks
          action match {
            case None =>
              commentId foreach (callIssueCommentWebHook(repository, issue, _, loginAccount, context.settings))
            case Some(act) =>
              val webHookAction = act match {
                case "close"  => "closed"
                case "reopen" => "reopened"
              }
              if (issue.isPullRequest)
                callPullRequestWebHook(webHookAction, repository, issue.issueId, loginAccount, context.settings)
              else
                callIssuesWebHook(webHookAction, repository, issue, loginAccount, context.settings)
          }

          // call hooks
          content foreach { x =>
            if (issue.isPullRequest)
              PluginRegistry().getPullRequestHooks.foreach(_.addedComment(commentId.get, x, issue, repository))
            else
              PluginRegistry().getIssueHooks.foreach(_.addedComment(commentId.get, x, issue, repository))
          }
          action foreach {
            case "close" =>
              if (issue.isPullRequest)
                PluginRegistry().getPullRequestHooks.foreach(_.closed(issue, repository))
              else
                PluginRegistry().getIssueHooks.foreach(_.closed(issue, repository))
            case "reopen" =>
              if (issue.isPullRequest)
                PluginRegistry().getPullRequestHooks.foreach(_.reopened(issue, repository))
              else
                PluginRegistry().getIssueHooks.foreach(_.reopened(issue, repository))
          }

          commentId.map(issue -> _)
      }
    }
  }
}
