package gitbucket.core.controller

import gitbucket.core.issues.milestones.html
import gitbucket.core.service.{RepositoryService, MilestonesService, AccountService}
import gitbucket.core.util.{ReferrerAuthenticator, CollaboratorsAuthenticator}
import gitbucket.core.util.Implicits._
import jp.sf.amateras.scalatra.forms._

class MilestonesController extends MilestonesControllerBase
  with MilestonesService with RepositoryService with AccountService
  with ReferrerAuthenticator with CollaboratorsAuthenticator

trait MilestonesControllerBase extends ControllerBase {
  self: MilestonesService with RepositoryService
    with ReferrerAuthenticator with CollaboratorsAuthenticator  =>

  case class MilestoneForm(title: String, description: Option[String], dueDate: Option[java.util.Date])

  val milestoneForm = mapping(
    "title"       -> trim(label("Title", text(required, maxlength(100)))),
    "description" -> trim(label("Description", optional(text()))),
    "dueDate"     -> trim(label("Due Date", optional(date())))
  )(MilestoneForm.apply)

  get("/:owner/:repository/issues/milestones")(referrersOnly { repository =>
    html.list(
      params.getOrElse("state", "open"),
      getMilestonesWithIssueCount(repository.owner, repository.name),
      repository,
      hasWritePermission(repository.owner, repository.name, context.loginAccount))
  })

  get("/:owner/:repository/issues/milestones/new")(collaboratorsOnly {
    html.edit(None, _)
  })

  post("/:owner/:repository/issues/milestones/new", milestoneForm)(collaboratorsOnly { (form, repository) =>
    createMilestone(repository.owner, repository.name, form.title, form.description, form.dueDate)
    redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/edit")(collaboratorsOnly { repository =>
    params("milestoneId").toIntOpt.map{ milestoneId =>
      html.edit(getMilestone(repository.owner, repository.name, milestoneId), repository)
    } getOrElse NotFound
  })

  post("/:owner/:repository/issues/milestones/:milestoneId/edit", milestoneForm)(collaboratorsOnly { (form, repository) =>
    params("milestoneId").toIntOpt.flatMap{ milestoneId =>
      getMilestone(repository.owner, repository.name, milestoneId).map { milestone =>
        updateMilestone(milestone.copy(title = form.title, description = form.description, dueDate = form.dueDate))
        redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
      }
    } getOrElse NotFound
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/close")(collaboratorsOnly { repository =>
    params("milestoneId").toIntOpt.flatMap{ milestoneId =>
      getMilestone(repository.owner, repository.name, milestoneId).map { milestone =>
        closeMilestone(milestone)
        redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
      }
    } getOrElse NotFound
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/open")(collaboratorsOnly { repository =>
    params("milestoneId").toIntOpt.flatMap{ milestoneId =>
      getMilestone(repository.owner, repository.name, milestoneId).map { milestone =>
        openMilestone(milestone)
        redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
      }
    } getOrElse NotFound
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/delete")(collaboratorsOnly { repository =>
    params("milestoneId").toIntOpt.flatMap{ milestoneId =>
      getMilestone(repository.owner, repository.name, milestoneId).map { milestone =>
        deleteMilestone(repository.owner, repository.name, milestone.milestoneId)
        redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
      }
    } getOrElse NotFound
  })

}
