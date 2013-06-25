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
    "newLabelName" -> trim(label("Label name", text(required, maxlength(100)))),
    "newColor"     -> trim(label("Color",      text(required, maxlength(7))))
  )(LabelForm.apply)

  val editForm = mapping(
    "editLabelName" -> trim(label("Label name", text(required, maxlength(100)))),
    "editColor"     -> trim(label("Color",      text(required, maxlength(7))))
  )(LabelForm.apply)

  post("/:owner/:repository/issues/label/new", newForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")

    createLabel(owner, repository, form.labelName, form.color.substring(1))

    redirect("/%s/%s/issues".format(owner, repository))
  })

  get("/:owner/:repository/issues/label/edit")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case None    => NotFound()
      case Some(r) => issues.html.labeleditlist(getLabels(owner, repository), r)
    }
  })

  get("/:owner/:repository/issues/label/:labelId/edit")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl) match {
      case None    => NotFound()
      case Some(r) => getLabel(owner, repository, labelId) match {
        case None    => NotFound()
        case Some(l) => issues.html.labeledit(Some(l), r)
      }
    }
  })

  post("/:owner/:repository/issues/label/:labelId/edit", editForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl) match {
      case None    => NotFound()
      case Some(r) => {
        updateLabel(owner, repository, labelId, form.labelName, form.color.substring(1))
        issues.html.labeleditlist(getLabels(owner, repository), r)
      }
    }
  })

  get("/:owner/:repository/issues/label/:labelId/delete")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getRepository(owner, repository, baseUrl) match {
      case None    => NotFound()
      case Some(r) => {
        deleteLabel(owner, repository, labelId)
        issues.html.labeleditlist(getLabels(owner, repository), r)
      }
    }
  })

}