package app

import jp.sf.amateras.scalatra.forms._

import service._
import util.{WritableRepositoryAuthenticator, ReadableRepositoryAuthenticator, UsersOnlyAuthenticator}

class MilestonesController extends MilestonesControllerBase
  with MilestonesService with RepositoryService with AccountService
  with ReadableRepositoryAuthenticator with WritableRepositoryAuthenticator

trait MilestonesControllerBase extends ControllerBase {
  self: MilestonesService with RepositoryService
    with ReadableRepositoryAuthenticator with WritableRepositoryAuthenticator  =>

  case class MilestoneForm(title: String, description: Option[String], dueDate: Option[java.util.Date])

  val milestoneForm = mapping(
    "title"       -> trim(label("Title", text(required, maxlength(100)))),
    "description" -> trim(label("Description", optional(text()))),
    "dueDate"     -> trim(label("Due Date", optional(date())))
  )(MilestoneForm.apply)

  get("/:owner/:repository/issues/milestones")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val state      = params.getOrElse("state", "open")

    getRepository(owner, repository, baseUrl).map { repositoryInfo =>
      issues.milestones.html.list(state,
        getMilestonesWithIssueCount(owner, repository),
        repositoryInfo,
        isWritable(owner, repository, context.loginAccount))
    } getOrElse NotFound
  })

  get("/:owner/:repository/issues/milestones/new")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl).map(issues.milestones.html.edit(None, _)) getOrElse NotFound
  })

  post("/:owner/:repository/issues/milestones/new", milestoneForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")

    createMilestone(owner, repository, form.title, form.description, form.dueDate)
    redirect("/%s/%s/issues/milestones".format(owner, repository))
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/edit")(writableRepository {
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getRepository(owner, repository, baseUrl).map(
      issues.milestones.html.edit(getMilestone(owner, repository, milestoneId), _)) getOrElse NotFound
  })

  post("/:owner/:repository/issues/milestones/:milestoneId/edit", milestoneForm)(writableRepository { form =>
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getMilestone(owner, repository, milestoneId).map { milestone =>
      updateMilestone(milestone.copy(title = form.title, description = form.description, dueDate = form.dueDate))
      redirect("/%s/%s/issues/milestones".format(owner, repository))
    } getOrElse NotFound
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/close")(writableRepository {
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getMilestone(owner, repository, milestoneId).map { milestone =>
      closeMilestone(milestone)
      redirect("/%s/%s/issues/milestones".format(owner, repository))
    } getOrElse NotFound
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/open")(writableRepository {
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getMilestone(owner, repository, milestoneId).map { milestone =>
      openMilestone(milestone)
      redirect("/%s/%s/issues/milestones".format(owner, repository))
    } getOrElse NotFound
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/delete")(writableRepository {
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getMilestone(owner, repository, milestoneId).map { _ =>
      deleteMilestone(owner, repository, milestoneId)
      redirect("/%s/%s/issues/milestones".format(owner, repository))
    } getOrElse NotFound
  })

}
