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
      comments = getComments(repository.owner, repository.name, issueId)
    } yield {
      JsonFormat(comments.map {
        case (comment, user, issue) =>
          ApiComment(comment, RepositoryName(repository), issueId, ApiUser(user), issue.isPullRequest)
      })
    }) getOrElse NotFound()
  })

  /*
   * ii. List comments in a repository
   * https://developer.github.com/v3/issues/comments/#list-comments-in-a-repository
   */

  /*
   * iii. Get an issue comment
   * https://docs.github.com/en/rest/reference/issues#get-an-issue-comment
   */
  get("/api/v3/repos/:owner/:repository/issues/comments/:id")(referrersOnly { repository =>
    val commentId = params("id").toInt
    getComment(repository.owner, repository.name, commentId) match {
      case Some((comment, user, issue)) =>
        JsonFormat(
          ApiComment(comment, RepositoryName(repository), issue.issueId, ApiUser(user), issue.isPullRequest)
        )
      case _ => NotFound()
    }
  })

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
      (comment, user, _) <- getComment(repository.owner, repository.name, id)
    } yield {
      JsonFormat(
        ApiComment(
          comment,
          RepositoryName(repository),
          issueId,
          ApiUser(user),
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
