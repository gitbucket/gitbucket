package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.MilestonesService
import gitbucket.core.service.RepositoryService.RepositoryInfo
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
  post("/api/v3/repos/:owner/:repository/milestones")(writableUsersOnly { repository =>
    (for {
      data <- extractFromJsonBody[CreateAMilestone] if data.isValid
      milestoneId = createMilestone(
        repository.owner,
        repository.name,
        data.title,
        data.description,
        data.due_on
      )
      apiMilestone <- getApiMilestone(repository, milestoneId)
    } yield {
      JsonFormat(apiMilestone)
    }) getOrElse NotFound()
  })

  /*
   * iii. Get a milestone
   * https://docs.github.com/en/rest/reference/issues#get-a-milestone
   */
  get("/api/v3/repos/:owner/:repository/milestones/:number")(referrersOnly { repository =>
    val milestoneId = params("number").toInt // use milestoneId as number
    (for (apiMilestone <- getApiMilestone(repository, milestoneId)) yield {
      JsonFormat(apiMilestone)
    }) getOrElse NotFound()
  })

  /*
   * iv.Update a milestone
   * https://docs.github.com/en/rest/reference/issues#update-a-milestone
   */
  patch("/api/v3/repos/:owner/:repository/milestones/:number")(writableUsersOnly { repository =>
    val milestoneId = params("number").toInt
    (for {
      data <- extractFromJsonBody[CreateAMilestone] if data.isValid
      milestone <- getMilestone(repository.owner, repository.name, milestoneId)
      _ = (data.state, milestone.closedDate) match {
        case ("open", Some(_)) =>
          openMilestone(milestone)
        case ("closed", None) =>
          closeMilestone(milestone)
        case _ =>
      }
      milestone <- getMilestone(repository.owner, repository.name, milestoneId)
      _ = updateMilestone(milestone.copy(title = data.title, description = data.description, dueDate = data.due_on))
      apiMilestone <- getApiMilestone(repository, milestoneId)
    } yield {
      JsonFormat(apiMilestone)
    }) getOrElse NotFound()
  })

  /*
   * v. Delete a milestone
   * https://docs.github.com/en/rest/reference/issues#delete-a-milestone
   */
  delete("/api/v3/repos/:owner/:repository/milestones/:number")(writableUsersOnly { repository =>
    val milestoneId = params("number").toInt // use milestoneId as number
    deleteMilestone(repository.owner, repository.name, milestoneId)
    NoContent()
  })

  private def getApiMilestone(repository: RepositoryInfo, milestoneId: Int): Option[ApiMilestone] = {
    getMilestonesWithIssueCount(repository.owner, repository.name)
      .find(p => p._1.milestoneId == milestoneId)
      .map(
        milestoneWithIssue =>
          ApiMilestone(
            repository.repository,
            milestoneWithIssue._1,
            milestoneWithIssue._2,
            milestoneWithIssue._3
        )
      )
  }
}
