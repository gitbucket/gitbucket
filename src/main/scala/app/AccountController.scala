package app

import service._
import util.OwnerOnlyAuthenticator
import jp.sf.amateras.scalatra.forms._

class AccountController extends AccountControllerBase
  with AccountService with RepositoryService with OwnerOnlyAuthenticator

trait AccountControllerBase extends ControllerBase {
  self: AccountService with RepositoryService with OwnerOnlyAuthenticator =>

  case class AccountEditForm(mailAddress: String, url: Option[String])

  val form = mapping(
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100)))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200)))))
  )(AccountEditForm.apply)

  /**
   * Displays user information.
   */
  get("/:userName") {
    val userName = params("userName")
    getAccountByUserName(userName) match {
      case Some(a) => account.html.userinfo(a, getVisibleRepositories(userName, baseUrl, context.loginAccount.map(_.userName)))
      case None => NotFound()
    }
  }

  get("/:userName/_edit")(ownerOnly {
    val userName = params("userName")
    getAccountByUserName(userName) match {
      case Some(a) => account.html.useredit(a)
      case None => NotFound()
    }
  })

  post("/:userName/_edit", form)(ownerOnly { form =>
    val userName = params("userName")
    val currentDate = new java.sql.Timestamp(System.currentTimeMillis)
    updateAccount(getAccountByUserName(userName).get.copy(
      mailAddress    = form.mailAddress,
      url            = form.url,
      updatedDate    = currentDate))

    redirect("/%s".format(userName))
  })

}
