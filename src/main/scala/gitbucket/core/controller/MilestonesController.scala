package gitbucket.core.controller

import gitbucket.core.issues.milestones.html
import gitbucket.core.service.IssuesService.{IssueLimit, IssueSearchCondition}
import gitbucket.core.service.{
  AccountService,
  CommitStatusService,
  IssueSearchOption,
  MilestonesService,
  RepositoryService
}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{ReferrerAuthenticator, WritableUsersAuthenticator}
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.view.helpers.{getAssignableUserNames, getLabels, getPriorities, searchIssue}
import org.scalatra.forms._
import org.scalatra.i18n.Messages

class MilestonesController
    extends MilestonesControllerBase
    with MilestonesService
    with RepositoryService
    with AccountService
    with CommitStatusService
    with ReferrerAuthenticator
    with WritableUsersAuthenticator

trait MilestonesControllerBase extends ControllerBase {
  self: MilestonesService
    with RepositoryService
    with CommitStatusService
    with ReferrerAuthenticator
    with WritableUsersAuthenticator =>

  case class MilestoneForm(title: String, description: Option[String], dueDate: Option[java.util.Date])

  val milestoneForm = mapping(
    "title" -> trim(label("Title", text(required, maxlength(100), uniqueMilestone))),
    "description" -> trim(label("Description", optional(text()))),
    "dueDate" -> trim(label("Due Date", optional(date())))
  )(MilestoneForm.apply)

  get("/:owner/:repository/issues/milestones")(referrersOnly { repository =>
    html.list(
      params.getOrElse("state", "open"),
      getMilestonesWithIssueCount(repository.owner, repository.name),
      repository,
      hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
    )
  })

  get("/:owner/:repository/milestone/:id")(referrersOnly { repository =>
    val milestone = getMilestone(repository.owner, repository.name, params("id").toInt)
    val page = IssueSearchCondition.page(request)
    val condition = IssueSearchCondition(
      request,
      milestone.get.title
    )
    val issues = searchIssue(
      condition,
      IssueSearchOption.Both,
      (page - 1) * IssueLimit,
      IssueLimit,
      repository.owner -> repository.name
    )
    val status = issues.map { issue =>
      issue.commitId.flatMap { commitId =>
        getCommitStatusWithSummary(issue.issue.userName, issue.issue.repositoryName, commitId)
      }
    }

    html.milestone(
      condition.state,
      issues.zip(status),
      page,
      getAssignableUserNames(repository.owner, repository.name),
      getPriorities(repository.owner, repository.name),
      getLabels(repository.owner, repository.name),
      condition,
      getMilestonesWithIssueCount(repository.owner, repository.name)
        .filter(p => p._1.milestoneId == milestone.get.milestoneId),
      repository,
      hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
    )
  })

  get("/:owner/:repository/issues/milestones/new")(writableUsersOnly {
    html.edit(None, _)
  })

  post("/:owner/:repository/issues/milestones/new", milestoneForm)(writableUsersOnly { (form, repository) =>
    createMilestone(repository.owner, repository.name, form.title, form.description, form.dueDate)
    redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/edit")(writableUsersOnly { repository =>
    params("milestoneId").toIntOpt.map { milestoneId =>
      html.edit(getMilestone(repository.owner, repository.name, milestoneId), repository)
    } getOrElse NotFound()
  })

  post("/:owner/:repository/issues/milestones/:milestoneId/edit", milestoneForm)(writableUsersOnly {
    (form, repository) =>
      params("milestoneId").toIntOpt.flatMap { milestoneId =>
        getMilestone(repository.owner, repository.name, milestoneId).map { milestone =>
          updateMilestone(milestone.copy(title = form.title, description = form.description, dueDate = form.dueDate))
          redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
        }
      } getOrElse NotFound()
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/close")(writableUsersOnly { repository =>
    params("milestoneId").toIntOpt.flatMap { milestoneId =>
      getMilestone(repository.owner, repository.name, milestoneId).map { milestone =>
        closeMilestone(milestone)
        redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
      }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/open")(writableUsersOnly { repository =>
    params("milestoneId").toIntOpt.flatMap { milestoneId =>
      getMilestone(repository.owner, repository.name, milestoneId).map { milestone =>
        openMilestone(milestone)
        redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
      }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/issues/milestones/:milestoneId/delete")(writableUsersOnly { repository =>
    params("milestoneId").toIntOpt.flatMap { milestoneId =>
      getMilestone(repository.owner, repository.name, milestoneId).map { milestone =>
        deleteMilestone(repository.owner, repository.name, milestone.milestoneId)
        redirect(s"/${repository.owner}/${repository.name}/issues/milestones")
      }
    } getOrElse NotFound()
  })

  private def uniqueMilestone: Constraint = new Constraint() {
    override def validate(
      name: String,
      value: String,
      params: Map[String, Seq[String]],
      messages: Messages
    ): Option[String] = {
      for {
        owner <- params.optionValue("owner")
        repository <- params.optionValue("repository")
        _ <- params.optionValue("milestoneId") match {
          // existing milestone
          case Some(id) =>
            getMilestones(owner, repository)
              .find(m => m.title.equalsIgnoreCase(value) && m.milestoneId.toString != id)
          // new milestone
          case None =>
            getMilestones(owner, repository)
              .find(m => m.title.equalsIgnoreCase(value))
        }
      } yield {
        "Milestone already exists."
      }
    }
  }
}
