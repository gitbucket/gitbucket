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

  get("/:owner/:repository/issues/label/:labelId/edit")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

    getLabel(owner, repository, labelId) match {
      case None    => NotFound()
      case Some(l) => issues.html.labeledit(Some(l), getRepository(owner, repository, baseUrl).get)
    }
  })

  post("/:owner/:repository/issues/label/:labelId/edit", editForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")
    val labelId    = params("labelId").toInt

//    createLabel(owner, repository, form.labelName, form.color.substring(1))
//
    Ok("label updated.")
  })

}