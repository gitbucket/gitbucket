package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, IssueCreationService, IssuesService, MilestonesService}
import gitbucket.core.util.{ReadableUsersAuthenticator, ReferrerAuthenticator}
import gitbucket.core.util.Implicits._

trait ApiIssueMilestoneControllerBase extends ControllerBase {
  self: AccountService
    with IssuesService
    with IssueCreationService
    with MilestonesService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator =>

  /*
   * i. List milestones
   * https://docs.github.com/en/rest/reference/issues#list-milestones
   */
  get("/api/v3/repos/:owner/:repository/milestones")(referrersOnly { repository =>
    val state = params.getOrElse("state", "all")
    // TODO "sort", "direction" params should be implemented.
    val apiMilestones = (for ((milestoneWithIssue, i) <- getMilestonesWithIssueCount(repository.owner, repository.name)
                                .sortBy(p => p._1.milestoneId)
                                .zipWithIndex)
      yield {
        ApiMilestone(
          repository.repository,
          milestoneWithIssue._1,
          ApiUser(context.loginAccount.get),
          i + 1,
          milestoneWithIssue._2,
          milestoneWithIssue._3
        )
      }).reverse
    state match {
      case "all" => JsonFormat(apiMilestones)
      case "open" | "closed" =>
        JsonFormat(
          apiMilestones.filter(p => p.state == state)
        )
      case _ => NotFound()
    }
  })

  /*
   * ii. Create a milestone
   * https://docs.github.com/en/rest/reference/issues#create-a-milestone
   */

  /*
   * iii. Get a milestone
   * https://docs.github.com/en/rest/reference/issues#get-a-milestone
   */
  get("/api/v3/repos/:owner/:repository/milestones/:number")(referrersOnly { repository =>
    val milestoneNumber = params("number").toInt
    val milestoneWithIssue = getMilestonesWithIssueCount(repository.owner, repository.name)
      .sortBy(p => p._1.milestoneId)
      .apply(milestoneNumber - 1)
    JsonFormat(
      ApiMilestone(
        repository.repository,
        milestoneWithIssue._1,
        ApiUser(context.loginAccount.get),
        milestoneNumber,
        milestoneWithIssue._2,
        milestoneWithIssue._3
      )
    )
  })

  /*
   * iv.Update a milestone
   * https://docs.github.com/en/rest/reference/issues#update-a-milestone
   */

  /*
 * v. Delete a milestone
 * https://docs.github.com/en/rest/reference/issues#delete-a-milestone
 */

}
