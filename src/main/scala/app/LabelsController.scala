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
    "newLabelName" -> trim(label("Label name", text(required, identifier, maxlength(100)))),
    "newColor"     -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  val editForm = mapping(
    "editLabelName" -> trim(label("Label name", text(required, identifier, maxlength(100)))),
    "editColor"     -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  post("/:owner/:repository/issues/label/new", newForm)(collaboratorsOnly { form =>
    val owner      = params("owner")
    val repository = params("repository")

    createLabel(owner, repository, form.labelName, form.color.substring(1))

    redirect("/%s/%s/issues".format(owner, repository))
  })

  ajaxGet("/:owner/:repository/issues/label/edit")(collaboratorsOnly {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl)
      .map(issues.labels.html.editlist(getLabels(owner, repository), _)) getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/issues/label/:labelId/edit")(collaboratorsOnly {
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl).map { repositoryInfo =>
      getLabel(owner, repository, labelId).map(label => issues.labels.html.edit(Some(label), repositoryInfo)) getOrElse NotFound()
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/issues/label/:labelId/edit", editForm)(collaboratorsOnly { form =>
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl).map{ repositoryInfo =>
      updateLabel(owner, repository, labelId, form.labelName, form.color.substring(1))
      issues.labels.html.editlist(getLabels(owner, repository), repositoryInfo)
    } getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/issues/label/:labelId/delete")(collaboratorsOnly {
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl).map { repositoryInfo =>
      deleteLabel(owner, repository, labelId)
      issues.labels.html.editlist(getLabels(owner, repository), repositoryInfo)
    } getOrElse NotFound()
  })

}