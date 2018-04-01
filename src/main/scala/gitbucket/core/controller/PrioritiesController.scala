package gitbucket.core.controller

import gitbucket.core.issues.priorities.html
import gitbucket.core.service.{
  RepositoryService,
  AccountService,
  IssuesService,
  LabelsService,
  MilestonesService,
  PrioritiesService
}
import gitbucket.core.util.{ReferrerAuthenticator, WritableUsersAuthenticator}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.SyntaxSugars._
import org.scalatra.forms._
import org.scalatra.i18n.Messages
import org.scalatra.Ok

class PrioritiesController
    extends PrioritiesControllerBase
    with IssuesService
    with RepositoryService
    with AccountService
    with LabelsService
    with PrioritiesService
    with MilestonesService
    with ReferrerAuthenticator
    with WritableUsersAuthenticator

trait PrioritiesControllerBase extends ControllerBase {
  self: PrioritiesService
    with IssuesService
    with RepositoryService
    with ReferrerAuthenticator
    with WritableUsersAuthenticator =>

  case class PriorityForm(priorityName: String, description: Option[String], color: String)

  val priorityForm = mapping(
    "priorityName" -> trim(label("Priority name", text(required, priorityName, uniquePriorityName, maxlength(100)))),
    "description" -> trim(label("Description", optional(text(maxlength(255))))),
    "priorityColor" -> trim(label("Color", text(required, color)))
  )(PriorityForm.apply)

  get("/:owner/:repository/issues/priorities")(referrersOnly { repository =>
    html.list(
      getPriorities(repository.owner, repository.name),
      countIssueGroupByPriorities(repository.owner, repository.name, IssuesService.IssueSearchCondition(), Map.empty),
      repository,
      hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
    )
  })

  ajaxGet("/:owner/:repository/issues/priorities/new")(writableUsersOnly { repository =>
    html.edit(None, repository)
  })

  ajaxPost("/:owner/:repository/issues/priorities/new", priorityForm)(writableUsersOnly { (form, repository) =>
    val priorityId =
      createPriority(repository.owner, repository.name, form.priorityName, form.description, form.color.substring(1))
    html.priority(
      getPriority(repository.owner, repository.name, priorityId).get,
      countIssueGroupByPriorities(repository.owner, repository.name, IssuesService.IssueSearchCondition(), Map.empty),
      repository,
      hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
    )
  })

  ajaxGet("/:owner/:repository/issues/priorities/:priorityId/edit")(writableUsersOnly { repository =>
    getPriority(repository.owner, repository.name, params("priorityId").toInt).map { priority =>
      html.edit(Some(priority), repository)
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/issues/priorities/:priorityId/edit", priorityForm)(writableUsersOnly {
    (form, repository) =>
      updatePriority(
        repository.owner,
        repository.name,
        params("priorityId").toInt,
        form.priorityName,
        form.description,
        form.color.substring(1)
      )
      html.priority(
        getPriority(repository.owner, repository.name, params("priorityId").toInt).get,
        countIssueGroupByPriorities(repository.owner, repository.name, IssuesService.IssueSearchCondition(), Map.empty),
        repository,
        hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
      )
  })

  ajaxPost("/:owner/:repository/issues/priorities/reorder")(writableUsersOnly { (repository) =>
    reorderPriorities(
      repository.owner,
      repository.name,
      params("order")
        .split(",")
        .map(id => id.toInt)
        .zipWithIndex
        .toMap
    )

    Ok()
  })

  ajaxPost("/:owner/:repository/issues/priorities/default")(writableUsersOnly { (repository) =>
    setDefaultPriority(repository.owner, repository.name, priorityId("priorityId"))
    Ok()
  })

  ajaxPost("/:owner/:repository/issues/priorities/:priorityId/delete")(writableUsersOnly { repository =>
    deletePriority(repository.owner, repository.name, params("priorityId").toInt)
    Ok()
  })

  val priorityId: String => Option[Int] = (key: String) => params.get(key).flatMap(_.toIntOpt)

  /**
   * Constraint for the identifier such as user name, repository name or page name.
   */
  private def priorityName: Constraint = new Constraint() {
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if (value.contains(',')) {
        Some(s"${name} contains invalid character.")
      } else if (value.startsWith("_") || value.startsWith("-")) {
        Some(s"${name} starts with invalid character.")
      } else {
        None
      }
  }

  private def uniquePriorityName: Constraint = new Constraint() {
    override def validate(
      name: String,
      value: String,
      params: Map[String, Seq[String]],
      messages: Messages
    ): Option[String] = {
      val owner = params.value("owner")
      val repository = params.value("repository")
      params
        .optionValue("priorityId")
        .map { priorityId =>
          getPriority(owner, repository, value)
            .filter(_.priorityId != priorityId.toInt)
            .map(_ => "Name has already been taken.")
        }
        .getOrElse {
          getPriority(owner, repository, value).map(_ => "Name has already been taken.")
        }
    }
  }
}
