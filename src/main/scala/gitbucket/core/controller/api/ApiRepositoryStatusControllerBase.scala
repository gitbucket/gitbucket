package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.CommitState
import gitbucket.core.service.{AccountService, CommitStatusService}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{JGitUtil, ReferrerAuthenticator, WritableUsersAuthenticator}

trait ApiRepositoryStatusControllerBase extends ControllerBase {
  self: AccountService with CommitStatusService with ReferrerAuthenticator with WritableUsersAuthenticator =>

  /*
   * i. Create a status
   * https://developer.github.com/v3/repos/statuses/#create-a-status
   */
  post("/api/v3/repos/:owner/:repository/statuses/:sha")(writableUsersOnly { repository =>
    (for {
      ref <- params.get("sha")
      sha <- JGitUtil.getShaByRef(repository.owner, repository.name, ref)
      data <- extractFromJsonBody[CreateAStatus] if data.isValid
      creator <- context.loginAccount
      state <- CommitState.valueOf(data.state)
      statusId = createCommitStatus(
        repository.owner,
        repository.name,
        sha,
        data.context.getOrElse("default"),
        state,
        data.target_url,
        data.description,
        new java.util.Date(),
        creator
      )
      status <- getCommitStatus(repository.owner, repository.name, statusId)
    } yield {
      JsonFormat(ApiCommitStatus(status, ApiUser(creator)))
    }) getOrElse NotFound()
  })

  /*
   * ii. List statuses for a specific ref
   * https://developer.github.com/v3/repos/statuses/#list-statuses-for-a-specific-ref
   * ref is Ref to list the statuses from. It can be a SHA, a branch name, or a tag name.
   */
  val listStatusesRoute = get("/api/v3/repos/:owner/:repository/commits/:ref/statuses")(referrersOnly { repository =>
    (for {
      ref <- params.get("ref")
      sha <- JGitUtil.getShaByRef(repository.owner, repository.name, ref)
    } yield {
      JsonFormat(getCommitStatusesWithCreator(repository.owner, repository.name, sha).map {
        case (status, creator) =>
          ApiCommitStatus(status, ApiUser(creator))
      })
    }) getOrElse NotFound()
  })

  /**
   * https://developer.github.com/v3/repos/statuses/#list-statuses-for-a-specific-ref
   * legacy route
   */
  get("/api/v3/repos/:owner/:repository/statuses/:ref") {
    listStatusesRoute.action()
  }

  /*
   * iii. Get the combined status for a specific ref
   * https://developer.github.com/v3/repos/statuses/#get-the-combined-status-for-a-specific-ref
   * ref is Ref to list the statuses from. It can be a SHA, a branch name, or a tag name.
   */
  get("/api/v3/repos/:owner/:repository/commits/:ref/status")(referrersOnly { repository =>
    (for {
      ref <- params.get("ref")
      owner <- getAccountByUserName(repository.owner)
      sha <- JGitUtil.getShaByRef(repository.owner, repository.name, ref)
    } yield {
      val statuses = getCommitStatusesWithCreator(repository.owner, repository.name, sha)
      JsonFormat(ApiCombinedCommitStatus(sha, statuses, ApiRepository(repository, owner)))
    }) getOrElse NotFound()
  })
}
