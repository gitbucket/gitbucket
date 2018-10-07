package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.{Account, Issue, PullRequest, Repository}
import gitbucket.core.service.{AccountService, IssuesService, PullRequestService, RepositoryService}
import gitbucket.core.service.IssuesService.IssueSearchCondition
import gitbucket.core.service.PullRequestService.PullRequestLimit
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.Implicits._
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.SyntaxSugars.using
import gitbucket.core.util.{ReferrerAuthenticator, RepositoryName}
import org.eclipse.jgit.api.Git
import scala.collection.JavaConverters._

trait ApiPullRequestControllerBase extends ControllerBase {
  self: AccountService with IssuesService with PullRequestService with RepositoryService with ReferrerAuthenticator =>

  /*
   * i. Link Relations
   * https://developer.github.com/v3/pulls/#link-relations
   */

  /*
   * ii. List pull requests
   * https://developer.github.com/v3/pulls/#list-pull-requests
   */
  /**
   * https://developer.github.com/v3/pulls/#list-pull-requests
   */
  get("/api/v3/repos/:owner/:repository/pulls")(referrersOnly { repository =>
    val page = IssueSearchCondition.page(request)
    // TODO: more api spec condition
    val condition = IssueSearchCondition(request)
    val baseOwner = getAccountByUserName(repository.owner).get

    val issues: List[(Issue, Account, Int, PullRequest, Repository, Account, Option[Account])] =
      searchPullRequestByApi(
        condition = condition,
        offset = (page - 1) * PullRequestLimit,
        limit = PullRequestLimit,
        repos = repository.owner -> repository.name
      )

    JsonFormat(issues.map {
      case (issue, issueUser, commentCount, pullRequest, headRepo, headOwner, assignee) =>
        ApiPullRequest(
          issue = issue,
          pullRequest = pullRequest,
          headRepo = ApiRepository(headRepo, ApiUser(headOwner)),
          baseRepo = ApiRepository(repository, ApiUser(baseOwner)),
          user = ApiUser(issueUser),
          labels = getIssueLabels(repository.owner, repository.name, issue.issueId)
            .map(ApiLabel(_, RepositoryName(repository))),
          assignee = assignee.map(ApiUser.apply),
          mergedComment = getMergedComment(repository.owner, repository.name, issue.issueId)
        )
    })
  })

  /*
   * iii. Get a single pull request
   * https://developer.github.com/v3/pulls/#get-a-single-pull-request
   */
  /**
   * https://developer.github.com/v3/pulls/#get-a-single-pull-request
   */
  get("/api/v3/repos/:owner/:repository/pulls/:id")(referrersOnly { repository =>
    (for {
      issueId <- params("id").toIntOpt
      (issue, pullRequest) <- getPullRequest(repository.owner, repository.name, issueId)
      users = getAccountsByUserNames(
        Set(repository.owner, pullRequest.requestUserName, issue.openedUserName),
        Set.empty
      )
      baseOwner <- users.get(repository.owner)
      headOwner <- users.get(pullRequest.requestUserName)
      issueUser <- users.get(issue.openedUserName)
      assignee = issue.assignedUserName.flatMap { userName =>
        getAccountByUserName(userName, false)
      }
      headRepo <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName)
    } yield {
      JsonFormat(
        ApiPullRequest(
          issue = issue,
          pullRequest = pullRequest,
          headRepo = ApiRepository(headRepo, ApiUser(headOwner)),
          baseRepo = ApiRepository(repository, ApiUser(baseOwner)),
          user = ApiUser(issueUser),
          labels = getIssueLabels(repository.owner, repository.name, issue.issueId)
            .map(ApiLabel(_, RepositoryName(repository))),
          assignee = assignee.map(ApiUser.apply),
          mergedComment = getMergedComment(repository.owner, repository.name, issue.issueId)
        )
      )
    }) getOrElse NotFound()
  })

  /*
   * iv. Create a pull request
   * https://developer.github.com/v3/pulls/#create-a-pull-request
   */

  /*
   * v. Update a pull request
   * https://developer.github.com/v3/pulls/#update-a-pull-request
   */

  /*
   * vi. List commits on a pull request
   * https://developer.github.com/v3/pulls/#list-commits-on-a-pull-request
   */
  /**
   * https://developer.github.com/v3/pulls/#list-commits-on-a-pull-request
   */
  get("/api/v3/repos/:owner/:repository/pulls/:id/commits")(referrersOnly { repository =>
    val owner = repository.owner
    val name = repository.name
    params("id").toIntOpt.flatMap {
      issueId =>
        getPullRequest(owner, name, issueId) map {
          case (issue, pullreq) =>
            using(Git.open(getRepositoryDir(owner, name))) { git =>
              val oldId = git.getRepository.resolve(pullreq.commitIdFrom)
              val newId = git.getRepository.resolve(pullreq.commitIdTo)
              val repoFullName = RepositoryName(repository)
              val commits = git.log
                .addRange(oldId, newId)
                .call
                .iterator
                .asScala
                .map { c =>
                  ApiCommitListItem(new CommitInfo(c), repoFullName)
                }
                .toList
              JsonFormat(commits)
            }
        }
    } getOrElse NotFound()
  })
  /*
   * vii. List pull requests files
   * https://developer.github.com/v3/pulls/#list-pull-requests-files
   */

  /*
   * viii. Get if a pull request has been merged
   * https://developer.github.com/v3/pulls/#get-if-a-pull-request-has-been-merged
   */

  /*
   * ix. Merge a pull request (Merge Button)
   * https://developer.github.com/v3/pulls/#merge-a-pull-request-merge-button
   */

  /*
 * x. Labels, assignees, and milestones
 * https://developer.github.com/v3/pulls/#labels-assignees-and-milestones
 */
}
