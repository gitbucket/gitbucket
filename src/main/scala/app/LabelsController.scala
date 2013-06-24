package app

import jp.sf.amateras.scalatra.forms._
import service._
import util.WritableRepositoryAuthenticator

class LabelsController extends LabelsControllerBase
  with LabelsService with RepositoryService with AccountService with WritableRepositoryAuthenticator

trait LabelsControllerBase extends ControllerBase {
  self: LabelsService with WritableRepositoryAuthenticator =>

  case class LabelForm(labelName: String, color: String)

  val labelForm = mapping(
    "newLabelName" -> trim(label("Label name", text(required, maxlength(100)))),
    "newColor"     -> trim(label("Color",      text(required, maxlength(7))))
  )(LabelForm.apply)

  post("/:owner/:repository/issues/label/new", labelForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")

    createLabel(owner, repository, form.labelName, form.color.substring(1))

    redirect("/%s/%s/issues".format(owner, repository))
  })

}