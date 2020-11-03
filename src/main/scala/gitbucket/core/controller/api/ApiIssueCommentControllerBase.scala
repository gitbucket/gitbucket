package gitbucket.core.controller.api
import gitbucket.core.api.{ApiComment, ApiUser, CreateAComment, JsonFormat}
import gitbucket.core.controller.{Context, ControllerBase}
import gitbucket.core.service._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{ReadableUsersAuthenticator, ReferrerAuthenticator, RepositoryName}
import org.scalatra.{ActionResult, NoContent}

trait ApiIssueCommentControllerBase extends ControllerBase {
  self: AccountService
    with IssuesService
    with RepositoryService
    with HandleCommentService
    with MilestonesService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator =>
  /*
   * i. List issue comments for a repository
   * https://docs.github.com/en/rest/reference/issues#list-issue-comments-for-a-repository
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
   * ii. Get an issue comment
   * https://docs.github.com/en/rest/reference/issues#get-an-issue-comment
   */
  get("/api/v3/repos/:owner/:repository/issues/comments/:id")(referrersOnly { repository =>
    val commentId = params("id").toInt
    getCommentForApi(repository.owner, repository.name, commentId) match {
      case Some((issueComment, user, issue)) =>
        JsonFormat(
          ApiComment(issueComment, RepositoryName(repository), issue.issueId, ApiUser(user), issue.isPullRequest)
        )
      case _ => NotFound()
    }
  })

  /*
   * iii. Update an issue comment
   * https://docs.github.com/en/rest/reference/issues#update-an-issue-comment
   */
  patch("/api/v3/repos/:owner/:repository/issues/comments/:id")(readableUsersOnly { repository =>
    val commentId = params("id")
    val result = for {
      issueComment <- getComment(repository.owner, repository.name, commentId)
      issue <- getIssue(repository.owner, repository.name, issueComment.issueId.toString)
    } yield {
      if (isEditable(repository.owner, repository.name, issueComment.commentedUserName)) {
        val body = extractFromJsonBody[CreateAComment].map(_.body)
        updateCommentByApi(repository, issue, issueComment.commentId.toString, body)
        getComment(repository.owner, repository.name, commentId) match {
          case Some(issueComment) =>
            JsonFormat(
              ApiComment(
                issueComment,
                RepositoryName(repository),
                issue.issueId,
                ApiUser(context.loginAccount.get),
                issue.isPullRequest
              )
            )
          case _ =>
        }
      } else {
        Unauthorized()
      }
    }
    result match {
      case Some(response) => response
      case None           => NotFound()
    }
  })

  /*
   * iv. Delete a comment
   * https://docs.github.com/en/rest/reference/issues#delete-an-issue-comment
   */
  delete("/api/v3/repos/:owner/:repo/issues/comments/:id")(readableUsersOnly { repository =>
    val maybeDeleteResponse: Option[Either[ActionResult, Option[Int]]] =
      for {
        commentId <- params("id").toIntOpt
        comment <- getComment(repository.owner, repository.name, commentId.toString)
        issue <- getIssue(repository.owner, repository.name, comment.issueId.toString)
      } yield {
        if (isEditable(repository.owner, repository.name, comment.commentedUserName)) {
          val maybeDeletedComment = deleteCommentByApi(repository, comment, issue)
          Right(maybeDeletedComment.map(_.commentId))
        } else {
          Left(Unauthorized())
        }
      }
    maybeDeleteResponse
      .map {
        case Right(maybeDeletedCommentId) => maybeDeletedCommentId.getOrElse(NotFound())
        case Left(err)                    => err
      }
      .getOrElse(NotFound())
  })

  /*
   * v. List issue comments
   * https://docs.github.com/en/rest/reference/issues#list-issue-comments
   */

  /*
   * vi. Create an issue comment
   * https://docs.github.com/en/rest/reference/issues#create-an-issue-comment
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

  private def isEditable(owner: String, repository: String, author: String)(implicit context: Context): Boolean =
    hasDeveloperRole(owner, repository, context.loginAccount) || author == context.loginAccount.get.userName
}
