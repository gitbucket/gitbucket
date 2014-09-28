package app

import jp.sf.amateras.scalatra.forms._
import service._
import util.{ReferrerAuthenticator, CollaboratorsAuthenticator}
import util.Implicits._
import org.scalatra.i18n.Messages

class LabelsController extends LabelsControllerBase
  with LabelsService with RepositoryService with AccountService
with ReferrerAuthenticator with CollaboratorsAuthenticator

trait LabelsControllerBase extends ControllerBase {
  self: LabelsService with RepositoryService
    with ReferrerAuthenticator with CollaboratorsAuthenticator =>

  case class LabelForm(labelName: String, color: String)

  val newForm = mapping(
    "newLabelName" -> trim(label("Label name", text(required, labelName, maxlength(100)))),
    "newColor"     -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  val editForm = mapping(
    "editLabelName" -> trim(label("Label name", text(required, labelName, maxlength(100)))),
    "editColor"     -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  get("/:owner/:repository/issues/labels")(referrersOnly { repository =>
    issues.labels.html.list(
      getLabels(repository.owner, repository.name),
      repository,
      hasWritePermission(repository.owner, repository.name, context.loginAccount))
  })

  ajaxGet("/:owner/:repository/issues/labels/new")(collaboratorsOnly { repository =>
    issues.labels.html.edit(None, repository)
  })

  post("/:owner/:repository/issues/labels/new", newForm)(collaboratorsOnly { (form, repository) =>
    createLabel(repository.owner, repository.name, form.labelName, form.color.substring(1))
    redirect(s"/${repository.owner}/${repository.name}/issues/labels")
  })

  ajaxGet("/:owner/:repository/issues/labels/:labelId/edit")(collaboratorsOnly { repository =>
    getLabel(repository.owner, repository.name, params("labelId").toInt).map { label =>
      issues.labels.html.edit(Some(label), repository)
    } getOrElse NotFound()
  })

  post("/:owner/:repository/issues/labels/:labelId/edit", editForm)(collaboratorsOnly { (form, repository) =>
    updateLabel(repository.owner, repository.name, params("labelId").toInt, form.labelName, form.color.substring(1))
    redirect(s"/${repository.owner}/${repository.name}/issues/labels")
  })

  get("/:owner/:repository/issues/labels/:labelId/delete")(collaboratorsOnly { repository =>
    deleteLabel(repository.owner, repository.name, params("labelId").toInt)
    redirect(s"/${repository.owner}/${repository.name}/issues/labels")
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

}
