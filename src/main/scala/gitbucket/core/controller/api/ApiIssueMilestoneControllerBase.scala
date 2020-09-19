package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.MilestonesService
import gitbucket.core.util.{ReferrerAuthenticator, WritableUsersAuthenticator}
import gitbucket.core.util.Implicits._
import org.scalatra.NoContent

trait ApiIssueMilestoneControllerBase extends ControllerBase {
  self: MilestonesService with WritableUsersAuthenticator with ReferrerAuthenticator =>

  /*
   * i. List milestones
   * https://docs.github.com/en/rest/reference/issues#list-milestones
   */
  get("/api/v3/repos/:owner/:repository/milestones")(referrersOnly { repository =>
    val state = params.getOrElse("state", "all")
    // TODO "sort", "direction" params should be implemented.
    val apiMilestones = (for (milestoneWithIssue <- getMilestonesWithIssueCount(repository.owner, repository.name)
                                .sortBy(p => p._1.milestoneId))
      yield {
        ApiMilestone(
          repository.repository,
          milestoneWithIssue._1,
          ApiUser(context.loginAccount.get),
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
    val milestoneId = params("number").toInt // use milestoneId as number
    getMilestonesWithIssueCount(repository.owner, repository.name)
      .find(p => p._1.milestoneId == milestoneId) match {
      case Some(milestoneWithIssue) =>
        JsonFormat(
          ApiMilestone(
            repository.repository,
            milestoneWithIssue._1,
            ApiUser(context.loginAccount.get),
            milestoneWithIssue._2,
            milestoneWithIssue._3
          )
        )
      case _ => NotFound()
    }
  })

  /*
   * iv.Update a milestone
   * https://docs.github.com/en/rest/reference/issues#update-a-milestone
   */

  /*
   * v. Delete a milestone
   * https://docs.github.com/en/rest/reference/issues#delete-a-milestone
   */
  delete("/api/v3/repos/:owner/:repository/milestones/:number")(writableUsersOnly { repository =>
    val milestoneId = params("number").toInt // use milestoneId as number
    deleteMilestone(repository.owner, repository.name, milestoneId)
    NoContent()
  })
}
