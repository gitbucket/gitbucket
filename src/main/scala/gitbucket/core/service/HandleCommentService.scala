package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.Issue
import gitbucket.core.model.Profile._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.Notifier
import profile.simple._

trait HandleCommentService {
  self: RepositoryService with IssuesService with ActivityService
    with WebHookService with WebHookIssueCommentService with WebHookPullRequestService =>

  /**
   * @see [[https://github.com/takezoe/gitbucket/wiki/CommentAction]]
   */
  def handleComment(issue: Issue, content: Option[String], repository: RepositoryService.RepositoryInfo, actionOpt: Option[String])
                   (implicit context: Context, s: Session) = {

    defining(repository.owner, repository.name){ case (owner, name) =>
      val userName = context.loginAccount.get.userName

      val (action, recordActivity) = actionOpt
        .collect {
          case "close" if(!issue.closed) => true  ->
            (Some("close")  -> Some(if(issue.isPullRequest) recordClosePullRequestActivity _ else recordCloseIssueActivity _))
          case "reopen" if(issue.closed) => false ->
            (Some("reopen") -> Some(recordReopenIssueActivity _))
        }
        .map { case (closed, t) =>
          updateClosed(owner, name, issue.issueId, closed)
          t
        }
        .getOrElse(None -> None)

      val commentId = (content, action) match {
        case (None, None) => None
        case (None, Some(action)) => Some(createComment(owner, name, userName, issue.issueId, action.capitalize, action))
        case (Some(content), _) => Some(createComment(owner, name, userName, issue.issueId, content, action.map(_+ "_comment").getOrElse("comment")))
      }

      // record comment activity if comment is entered
      content foreach {
        (if(issue.isPullRequest) recordCommentPullRequestActivity _ else recordCommentIssueActivity _)
        (owner, name, userName, issue.issueId, _)
      }
      recordActivity foreach ( _ (owner, name, userName, issue.issueId, issue.title) )

      // extract references and create refer comment
      content.map { content =>
        createReferComment(owner, name, issue, content, context.loginAccount.get)
      }

      // call web hooks
      action match {
        case None => commentId.map{ commentIdSome => callIssueCommentWebHook(repository, issue, commentIdSome, context.loginAccount.get) }
        case Some(act) => val webHookAction = act match {
          case "open"   => "opened"
          case "reopen" => "reopened"
          case "close"  => "closed"
          case _ => act
        }
          if(issue.isPullRequest){
            callPullRequestWebHook(webHookAction, repository, issue.issueId, context.baseUrl, context.loginAccount.get)
          } else {
            callIssuesWebHook(webHookAction, repository, issue, context.baseUrl, context.loginAccount.get)
          }
      }

      // notifications
      Notifier() match {
        case f =>
          content foreach {
            f.toNotify(repository, issue, _){
              Notifier.msgComment(s"${context.baseUrl}/${owner}/${name}/${
                if(issue.isPullRequest) "pull" else "issues"}/${issue.issueId}#comment-${commentId.get}")
            }
          }
          action foreach {
            f.toNotify(repository, issue, _){
              Notifier.msgStatus(s"${context.baseUrl}/${owner}/${name}/issues/${issue.issueId}")
            }
          }
      }

      commentId.map( issue -> _ )
    }
  }

}
