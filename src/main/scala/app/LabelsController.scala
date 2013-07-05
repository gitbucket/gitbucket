package app

import jp.sf.amateras.scalatra.forms._
import service._
import util.CollaboratorsAuthenticator

class LabelsController extends LabelsControllerBase
  with LabelsService with RepositoryService with AccountService with CollaboratorsAuthenticator

trait LabelsControllerBase extends ControllerBase {
  self: LabelsService with RepositoryService with CollaboratorsAuthenticator =>

  case class LabelForm(labelName: String, color: String)

  val newForm = mapping(
    "newLabelName" -> trim(label("Label name", text(required, labelName, maxlength(100)))),
    "newColor"     -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  val editForm = mapping(
    "editLabelName" -> trim(label("Label name", text(required, labelName, maxlength(100)))),
    "editColor"     -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  post("/:owner/:repository/issues/label/new", newForm)(collaboratorsOnly { (form, repository) =>
    createLabel(repository.owner, repository.name, form.labelName, form.color.substring(1))
    redirect("/%s/%s/issues".format(repository.owner, repository.name))
  })

  ajaxGet("/:owner/:repository/issues/label/edit")(collaboratorsOnly { repository =>
    issues.labels.html.editlist(getLabels(repository.owner, repository.name), repository)
  })

  ajaxGet("/:owner/:repository/issues/label/:labelId/edit")(collaboratorsOnly { repository =>
    getLabel(repository.owner, repository.name, params("labelId").toInt).map { label =>
      issues.labels.html.edit(Some(label), repository)
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/issues/label/:labelId/edit", editForm)(collaboratorsOnly { (form, repository) =>
    updateLabel(repository.owner, repository.name, params("labelId").toInt, form.labelName, form.color.substring(1))
    issues.labels.html.editlist(getLabels(repository.owner, repository.name), repository)
  })

  ajaxGet("/:owner/:repository/issues/label/:labelId/delete")(collaboratorsOnly { repository =>
    deleteLabel(repository.owner, repository.name, params("labelId").toInt)
    issues.labels.html.editlist(getLabels(repository.owner, repository.name), repository)
  })

  /**
   * Constraint for the identifier such as user name, repository name or page name.
   */
  private def labelName: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      if(!value.matches("^[^,]+$")){
        Some("%s contains invalid character.".format(name))
      } else if(value.startsWith("_") || value.startsWith("-")){
        Some("%s starts with invalid character.".format(name))
      } else {
        None
      }
  }

}