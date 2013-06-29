package app

import service._
import util.OwnerOnlyAuthenticator
import util.StringUtil._
import jp.sf.amateras.scalatra.forms._

class AccountController extends AccountControllerBase
  with SystemSettingsService with AccountService with RepositoryService with OwnerOnlyAuthenticator

trait AccountControllerBase extends ControllerBase {
  self: SystemSettingsService with AccountService with RepositoryService with OwnerOnlyAuthenticator =>

  case class AccountNewForm(userName: String, password: String,mailAddress: String, url: Option[String])

  case class AccountEditForm(password: Option[String], mailAddress: String, url: Option[String])

  val newForm = mapping(
    "userName"    -> trim(label("User name"    , text(required, maxlength(100)))),
    "password"    -> trim(label("Password"     , text(required, maxlength(20)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100)))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200)))))
  )(AccountNewForm.apply)

  val editForm = mapping(
    "password"    -> trim(label("Password"     , optional(text(maxlength(20))))),
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
    } getOrElse NotFound
  }

  get("/:userName/_edit")(ownerOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map(x => account.html.useredit(Some(x))) getOrElse NotFound
  })

  post("/:userName/_edit", editForm)(ownerOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      updateAccount(account.copy(
        password    = form.password.map(encrypt).getOrElse(account.password),
        mailAddress = form.mailAddress,
        url         = form.url))
      redirect("/%s".format(userName))
    } getOrElse NotFound
  })

  get("/register"){
    if(loadSystemSettings().allowAccountRegistration){
      account.html.useredit(None)
    } else NotFound
  }

  post("/register", newForm){ newForm =>
    if(loadSystemSettings().allowAccountRegistration){
      createAccount(newForm.userName, encrypt(newForm.password), newForm.mailAddress, false, newForm.url)
      redirect("/signin")
    } else NotFound
  }
}
