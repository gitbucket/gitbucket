package app

import jp.sf.amateras.scalatra.forms._
import service._
import util.WritableRepositoryAuthenticator
import org.scalatra._

class LabelsController extends LabelsControllerBase
  with LabelsService with RepositoryService with AccountService with WritableRepositoryAuthenticator

trait LabelsControllerBase extends ControllerBase {
  self: LabelsService with RepositoryService with WritableRepositoryAuthenticator =>

  case class LabelForm(labelName: String, color: String)

  val newForm = mapping(
    "newLabelName" -> trim(label("Label name", text(required, identifier, maxlength(100)))),
    "newColor"     -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  val editForm = mapping(
    "editLabelName" -> trim(label("Label name", text(required, identifier, maxlength(100)))),
    "editColor"     -> trim(label("Color",      text(required, color)))
  )(LabelForm.apply)

  post("/:owner/:repository/issues/label/new", newForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")

    createLabel(owner, repository, form.labelName, form.color.substring(1))

    redirect("/%s/%s/issues".format(owner, repository))
  })

  ajaxGet("/:owner/:repository/issues/label/edit")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl)
      .map(issues.html.labeleditlist(getLabels(owner, repository), _)) getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/issues/label/:labelId/edit")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl).map { repositoryInfo =>
      getLabel(owner, repository, labelId).map(label => issues.html.labeledit(Some(label), repositoryInfo)) getOrElse NotFound()
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/issues/label/:labelId/edit", editForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl).map{ repositoryInfo =>
      updateLabel(owner, repository, labelId, form.labelName, form.color.substring(1))
      issues.html.labeleditlist(getLabels(owner, repository), repositoryInfo)
    } getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/issues/label/:labelId/delete")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl).map { repositoryInfo =>
      deleteLabel(owner, repository, labelId)
      issues.html.labeleditlist(getLabels(owner, repository), repositoryInfo)
    } getOrElse NotFound()
  })

}