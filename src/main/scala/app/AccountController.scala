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
    getAccountByUserName(userName).map {
      account.html.userinfo(_, getVisibleRepositories(userName, baseUrl, context.loginAccount.map(_.userName)))
    } getOrElse NotFound()
  }

  get("/:userName/_edit")(ownerOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map(account.html.useredit(_)) getOrElse NotFound()
  })

  post("/:userName/_edit", form)(ownerOnly { form =>
    val userName = params("userName")
    updateAccount(getAccountByUserName(userName).get.copy(mailAddress = form.mailAddress, url = form.url))
    redirect("/%s".format(userName))
  })

}
