package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.{Account, Issue}
import gitbucket.core.service.{AccountService, IssueCreationService, IssuesService, MilestonesService}
import gitbucket.core.service.IssuesService.IssueSearchCondition
import gitbucket.core.service.PullRequestService.PullRequestLimit
import gitbucket.core.util.{ReadableUsersAuthenticator, ReferrerAuthenticator, RepositoryName, UsersAuthenticator}
import gitbucket.core.util.Implicits._

trait ApiIssueControllerBase extends ControllerBase {
  self: AccountService
    with IssuesService
    with IssueCreationService
    with MilestonesService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator =>
  /*
   * i. List issues
   * https://developer.github.com/v3/issues/#list-issues
   * requested: 1743
   */

  /*
   * ii. List issues for a repository
   * https://developer.github.com/v3/issues/#list-issues-for-a-repository
   */
  get("/api/v3/repos/:owner/:repository/issues")(referrersOnly { repository =>
    val page = IssueSearchCondition.page(request)
    // TODO: more api spec condition
    val condition = IssueSearchCondition(request)
    val baseOwner = getAccountByUserName(repository.owner).get

    val issues: List[(Issue, Account, Option[Account])] =
      searchIssueByApi(
        condition = condition,
        offset = (page - 1) * PullRequestLimit,
        limit = PullRequestLimit,
        repos = repository.owner -> repository.name
      )

    JsonFormat(issues.map {
      case (issue, issueUser, assignedUser) =>
        ApiIssue(
          issue = issue,
          repositoryName = RepositoryName(repository),
          user = ApiUser(issueUser),
          assignee = assignedUser.map(ApiUser(_)),
          labels = getIssueLabels(repository.owner, repository.name, issue.issueId)
            .map(ApiLabel(_, RepositoryName(repository)))
        )
    })
  })

  /*
   * iii. Get a single issue
   * https://developer.github.com/v3/issues/#get-a-single-issue
   */
  get("/api/v3/repos/:owner/:repository/issues/:id")(referrersOnly { repository =>
    (for {
      issueId <- params("id").toIntOpt
      issue <- getIssue(repository.owner, repository.name, issueId.toString)
      users = getAccountsByUserNames(Set(issue.openedUserName) ++ issue.assignedUserName, Set())
      openedUser <- users.get(issue.openedUserName)
    } yield {
      JsonFormat(
        ApiIssue(
          issue,
          RepositoryName(repository),
          ApiUser(openedUser),
          issue.assignedUserName.flatMap(users.get(_)).map(ApiUser(_)),
          getIssueLabels(repository.owner, repository.name, issue.issueId).map(ApiLabel(_, RepositoryName(repository)))
        )
      )
    }) getOrElse NotFound()
  })

  /*
   * iv. Create an issue
   * https://developer.github.com/v3/issues/#create-an-issue
   */
  post("/api/v3/repos/:owner/:repository/issues")(readableUsersOnly { repository =>
    if (isIssueEditable(repository)) { // TODO Should this check is provided by authenticator?
      (for {
        data <- extractFromJsonBody[CreateAnIssue]
        loginAccount <- context.loginAccount
      } yield {
        val milestone = data.milestone.flatMap(getMilestone(repository.owner, repository.name, _))
        val issue = createIssue(
          repository,
          data.title,
          data.body,
          data.assignees.headOption,
          milestone.map(_.milestoneId),
          None,
          data.labels,
          loginAccount
        )
        JsonFormat(
          ApiIssue(
            issue,
            RepositoryName(repository),
            ApiUser(loginAccount),
            issue.assignedUserName.flatMap(getAccountByUserName(_)).map(ApiUser(_)),
            getIssueLabels(repository.owner, repository.name, issue.issueId)
              .map(ApiLabel(_, RepositoryName(repository)))
          )
        )
      }) getOrElse NotFound()
    } else Unauthorized()
  })
  /*
   * v. Edit an issue
   * https://developer.github.com/v3/issues/#edit-an-issue
   */

  /*
   * vi. Lock an issue
   * https://developer.github.com/v3/issues/#lock-an-issue
   */

  /*
 * vii. Unlock an issue
 * https://developer.github.com/v3/issues/#unlock-an-issue
 */
}
