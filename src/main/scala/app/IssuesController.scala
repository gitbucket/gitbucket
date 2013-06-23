package app

import jp.sf.amateras.scalatra.forms._

import service._
import util.{WritableRepositoryAuthenticator, ReadableRepositoryAuthenticator, UsersOnlyAuthenticator}

class IssuesController extends IssuesControllerBase
  with IssuesService with RepositoryService with AccountService
  with UsersOnlyAuthenticator with ReadableRepositoryAuthenticator with WritableRepositoryAuthenticator

trait IssuesControllerBase extends ControllerBase {
  self: IssuesService with RepositoryService
    with UsersOnlyAuthenticator with ReadableRepositoryAuthenticator with WritableRepositoryAuthenticator  =>

  case class IssueForm(title: String, content: Option[String])

  case class MilestoneForm(title: String, description: Option[String], dueDate: Option[java.util.Date])

  val form = mapping(
      "title"   -> trim(label("Title", text(required))),
      "content" -> trim(optional(text()))
    )(IssueForm.apply)

  val milestoneForm = mapping(
    "title"       -> trim(label("Title", text(required, maxlength(100)))),
    "description" -> trim(label("Description", optional(text()))),
    "dueDate"     -> trim(label("Due Date", optional(date())))
  )(MilestoneForm.apply)

  get("/:owner/:repository/issues"){
    val owner = params("owner")
    val repository = params("repository")

    // search condition
    val closed = params.get("state") collect {
      case "closed" => true
    } getOrElse false

    issues.html.issues(searchIssue(owner, repository, closed),
        getRepository(params("owner"), params("repository"), baseUrl).get)
  }

  get("/:owner/:repository/issues/:id"){
    val owner = params("owner")
    val repository = params("repository")
    val issueId = params("id")

    getIssue(owner, repository, issueId) map {
      issues.html.issue(_, getRepository(owner, repository, baseUrl).get)
    } getOrElse NotFound
  }

  get("/:owner/:repository/issues/new")( usersOnly {
    issues.html.issueedit(getRepository(params("owner"), params("repository"), baseUrl).get)
  })

  post("/:owner/:repository/issues", form)( usersOnly { form =>
    val owner = params("owner")
    val repository = params("repository")

    redirect("/%s/%s/issues/%d".format(owner, repository,
        saveIssue(owner, repository, context.loginAccount.get.userName, form.title, form.content)))
  })

  get("/:owner/:repository/issues/milestones")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val state      = params.getOrElse("state", "open")

    getRepository(owner, repository, baseUrl) match {
      case None    => NotFound()
      case Some(r) => issues.html.milestones(state, getMilestones(owner, repository),
        getMilestoneIssueCounts(owner, repository), r, isWritable(owner, repository, context.loginAccount))
    }
  })

  get("/:owner/:repository/issues/milestones/new")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case None    => NotFound()
      case Some(r) => issues.html.milestoneedit(None, r)
    }
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

    getRepository(owner, repository, baseUrl) match {
      case None    => NotFound()
      case Some(r) => issues.html.milestoneedit(getMilestone(owner, repository, milestoneId), r)
    }
  })

  post("/:owner/:repository/issues/milestones/:milestoneId/edit", milestoneForm)(writableRepository { form =>
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getMilestone(owner, repository, milestoneId) match {
      case None    => NotFound()
      case Some(m) => {
        updateMilestone(m.copy(title = form.title, description = form.description, dueDate = form.dueDate))
        redirect("/%s/%s/issues/milestones".format(owner, repository))
      }
    }
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/close")(writableRepository {
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getMilestone(owner, repository, milestoneId) match {
      case None    => NotFound()
      case Some(m) => {
        closeMilestone(m)
        redirect("/%s/%s/issues/milestones".format(owner, repository))
      }
    }
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/open")(writableRepository {
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getMilestone(owner, repository, milestoneId) match {
      case None    => NotFound()
      case Some(m) => {
        openMilestone(m)
        redirect("/%s/%s/issues/milestones".format(owner, repository))
      }
    }
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/delete")(writableRepository {
    val owner       = params("owner")
    val repository  = params("repository")
    val milestoneId = params("milestoneId").toInt

    getMilestone(owner, repository, milestoneId) match {
      case None    => NotFound()
      case Some(m) => {
        deleteMilestone(owner, repository, milestoneId)
        redirect("/%s/%s/issues/milestones".format(owner, repository))
      }
    }
  })
}