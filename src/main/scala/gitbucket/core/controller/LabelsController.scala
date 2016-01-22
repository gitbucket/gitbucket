package gitbucket.core.controller

import gitbucket.core.api.{ApiError, CreateALabel, ApiLabel, JsonFormat}
import gitbucket.core.issues.labels.html
import gitbucket.core.service.{RepositoryService, AccountService, IssuesService, LabelsService}
import gitbucket.core.util.{LockUtil, RepositoryName, ReferrerAuthenticator, CollaboratorsAuthenticator}
import gitbucket.core.util.Implicits._
import io.github.gitbucket.scalatra.forms._
import org.scalatra.i18n.Messages
import org.scalatra.{NoContent, UnprocessableEntity, Created, Ok}

class LabelsController extends LabelsControllerBase
  with LabelsService with IssuesService with RepositoryService with AccountService
with ReferrerAuthenticator with CollaboratorsAuthenticator

trait LabelsControllerBase extends ControllerBase {
  self: LabelsService with IssuesService with RepositoryService
    with ReferrerAuthenticator with CollaboratorsAuthenticator =>

  case class LabelForm(labelName: String, color: String)

  val labelForm = mapping(
    "labelName"  -> trim(label("Label name", text(required, labelName, uniqueLabelName, maxlength(100)))),
    "labelColor" -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  get("/:owner/:repository/issues/labels")(referrersOnly { repository =>
    html.list(
      getLabels(repository.owner, repository.name),
      countIssueGroupByLabels(repository.owner, repository.name, IssuesService.IssueSearchCondition(), Map.empty),
      repository,
      hasWritePermission(repository.owner, repository.name, context.loginAccount))
  })

  /**
    * List all labels for this repository
    * https://developer.github.com/v3/issues/labels/#list-all-labels-for-this-repository
    */
  get("/api/v3/repos/:owner/:repository/labels")(referrersOnly { repository =>
    JsonFormat(getLabels(repository.owner, repository.name).map { label =>
      ApiLabel(label, RepositoryName(repository))
    })
  })

  /**
    * Get a single label
    * https://developer.github.com/v3/issues/labels/#get-a-single-label
    */
  get("/api/v3/repos/:owner/:repository/labels/:labelName")(referrersOnly { repository =>
    getLabel(repository.owner, repository.name, params("labelName")).map { label =>
      JsonFormat(ApiLabel(label, RepositoryName(repository)))
    } getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/issues/labels/new")(collaboratorsOnly { repository =>
    html.edit(None, repository)
  })

  ajaxPost("/:owner/:repository/issues/labels/new", labelForm)(collaboratorsOnly { (form, repository) =>
    val labelId = createLabel(repository.owner, repository.name, form.labelName, form.color.substring(1))
    html.label(
      getLabel(repository.owner, repository.name, labelId).get,
      // TODO futility
      countIssueGroupByLabels(repository.owner, repository.name, IssuesService.IssueSearchCondition(), Map.empty),
      repository,
      hasWritePermission(repository.owner, repository.name, context.loginAccount))
  })

  /**
    * Create a label
    * https://developer.github.com/v3/issues/labels/#create-a-label
    */
  post("/api/v3/repos/:owner/:repository/labels")(collaboratorsOnly { repository =>
    (for{
      data <- extractFromJsonBody[CreateALabel] if data.isValid
    } yield {
      LockUtil.lock(RepositoryName(repository).fullName) {
        if (getLabel(repository.owner, repository.name, data.name).isEmpty) {
          val labelId = createLabel(repository.owner, repository.name, data.name, data.color)
          getLabel(repository.owner, repository.name, labelId).map { label =>
            Created(JsonFormat(ApiLabel(label, RepositoryName(repository))))
          } getOrElse NotFound()
        } else {
          // TODO ApiError should support errors field to enhance compatibility of GitHub API
          UnprocessableEntity(ApiError(
            "Validation Failed",
            Some("https://developer.github.com/v3/issues/labels/#create-a-label")
          ))
        }
      }
    }) getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/issues/labels/:labelId/edit")(collaboratorsOnly { repository =>
    getLabel(repository.owner, repository.name, params("labelId").toInt).map { label =>
      html.edit(Some(label), repository)
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/issues/labels/:labelId/edit", labelForm)(collaboratorsOnly { (form, repository) =>
    updateLabel(repository.owner, repository.name, params("labelId").toInt, form.labelName, form.color.substring(1))
    html.label(
      getLabel(repository.owner, repository.name, params("labelId").toInt).get,
      // TODO futility
      countIssueGroupByLabels(repository.owner, repository.name, IssuesService.IssueSearchCondition(), Map.empty),
      repository,
      hasWritePermission(repository.owner, repository.name, context.loginAccount))
  })

  /**
    * Update a label
    * https://developer.github.com/v3/issues/labels/#update-a-label
    */
  patch("/api/v3/repos/:owner/:repository/labels/:labelName")(collaboratorsOnly { repository =>
    (for{
      data <- extractFromJsonBody[CreateALabel] if data.isValid
    } yield {
      LockUtil.lock(RepositoryName(repository).fullName) {
        getLabel(repository.owner, repository.name, params("labelName")).map { label =>
          if (getLabel(repository.owner, repository.name, data.name).isEmpty) {
            updateLabel(repository.owner, repository.name, label.labelId, data.name, data.color)
            JsonFormat(ApiLabel(
              getLabel(repository.owner, repository.name, label.labelId).get,
              RepositoryName(repository)))
          } else {
            // TODO ApiError should support errors field to enhance compatibility of GitHub API
            UnprocessableEntity(ApiError(
              "Validation Failed",
              Some("https://developer.github.com/v3/issues/labels/#create-a-label")))
          }
        } getOrElse NotFound()
      }
    }) getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/issues/labels/:labelId/delete")(collaboratorsOnly { repository =>
    deleteLabel(repository.owner, repository.name, params("labelId").toInt)
    Ok()
  })

  /**
    * Delete a label
    * https://developer.github.com/v3/issues/labels/#delete-a-label
    */
  delete("/api/v3/repos/:owner/:repository/labels/:labelName")(collaboratorsOnly { repository =>
    LockUtil.lock(RepositoryName(repository).fullName) {
      getLabel(repository.owner, repository.name, params("labelName")).map { label =>
        deleteLabel(repository.owner, repository.name, label.labelId)
        NoContent()
      } getOrElse NotFound()
    }
  })

  /**
   * Constraint for the identifier such as user name, repository name or page name.
   */
  private def labelName: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(value.contains(',')){
        Some(s"${name} contains invalid character.")
      } else if(value.startsWith("_") || value.startsWith("-")){
        Some(s"${name} starts with invalid character.")
      } else {
        None
      }
  }

  private def uniqueLabelName: Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Option[String] = {
      val owner = params("owner")
      val repository = params("repository")
      getLabel(owner, repository, value).map(_ => "Name has already been taken.")
    }
  }

}
