package gitbucket.core.controller.api
import gitbucket.core.api.{ApiComment, ApiUser, CreateAComment, JsonFormat}
import gitbucket.core.controller.{Context, ControllerBase}
import gitbucket.core.service._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{ReadableUsersAuthenticator, ReferrerAuthenticator, RepositoryName}

trait ApiIssueCommentControllerBase extends ControllerBase {
  self: AccountService
    with IssuesService
    with RepositoryService
    with HandleCommentService
    with MilestonesService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator =>
  /*
   * i. List comments on an issue
   * https://developer.github.com/v3/issues/comments/#list-comments-on-an-issue
   */
  get("/api/v3/repos/:owner/:repository/issues/:id/comments")(referrersOnly { repository =>
    (for {
      issueId <- params("id").toIntOpt
      comments = getCommentsForApi(repository.owner, repository.name, issueId)
    } yield {
      JsonFormat(comments.map {
        case (issueComment, user, issue) =>
          ApiComment(issueComment, RepositoryName(repository), issueId, ApiUser(user), issue.isPullRequest)
      })
    }) getOrElse NotFound()
  })

  /*
   * ii. List comments in a repository
   * https://developer.github.com/v3/issues/comments/#list-comments-in-a-repository
   */

  /*
   * iii. Get a single comment
   * https://developer.github.com/v3/issues/comments/#get-a-single-comment
   */

  /*
   * iv. Create a comment
   * https://developer.github.com/v3/issues/comments/#create-a-comment
   */
  post("/api/v3/repos/:owner/:repository/issues/:id/comments")(readableUsersOnly { repository =>
    (for {
      issueId <- params("id").toIntOpt
      issue <- getIssue(repository.owner, repository.name, issueId.toString)
      body <- extractFromJsonBody[CreateAComment].map(_.body) if !body.isEmpty
      action = params.get("action").filter(_ => isEditable(issue.userName, issue.repositoryName, issue.openedUserName))
      (issue, id) <- handleComment(issue, Some(body), repository, action)
      issueComment <- getComment(repository.owner, repository.name, id.toString())
    } yield {
      JsonFormat(
        ApiComment(
          issueComment,
          RepositoryName(repository),
          issueId,
          ApiUser(context.loginAccount.get),
          issue.isPullRequest
        )
      )
    }) getOrElse NotFound()
  })

  /*
   * v. Edit a comment
   * https://developer.github.com/v3/issues/comments/#edit-a-comment
   */

  /*
   * vi. Delete a comment
   * https://developer.github.com/v3/issues/comments/#delete-a-comment
   */

  private def isEditable(owner: String, repository: String, author: String)(implicit context: Context): Boolean =
    hasDeveloperRole(owner, repository, context.loginAccount) || author == context.loginAccount.get.userName
}
