package app

import model._
import service._
import util.AdminOnlyAuthenticator
import jp.sf.amateras.scalatra.forms._

class UserManagementController extends UserManagementControllerBase with AccountService with AdminOnlyAuthenticator

trait UserManagementControllerBase extends ControllerBase { self: AccountService with AdminOnlyAuthenticator =>
  
  case class UserForm(userName: String, password: String, mailAddress: String, isAdmin: Boolean, url: Option[String])
  
  val newForm = mapping(
    "userName"    -> trim(label("Username"     , text(required, maxlength(100), identifier, unique))),
    "password"    -> trim(label("Password"     , text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100)))),
    "isAdmin"     -> trim(label("User Type"    , boolean())),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200)))))
  )(UserForm.apply)

  val editForm = mapping(
    "userName"    -> trim(label("Username"     , text(required, maxlength(100), identifier))),
    "password"    -> trim(label("Password"     , text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100)))),
    "isAdmin"     -> trim(label("User Type"    , boolean())),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200)))))
  )(UserForm.apply)
  
  get("/admin/users")(adminOnly {
    admin.html.userlist(getAllUsers())
  })
  
  get("/admin/users/_new")(adminOnly {
    admin.html.useredit(None)
  })
  
  post("/admin/users/_new", newForm)(adminOnly { form =>
    createAccount(form.userName, form.password, form.mailAddress, form.isAdmin, form.url)
    redirect("/admin/users")
  })
  
  get("/admin/users/:userName/_edit")(adminOnly {
    val userName = params("userName")
    admin.html.useredit(getAccountByUserName(userName))
  })
  
  post("/admin/users/:name/_edit", editForm)(adminOnly { form =>
    val userName = params("userName")
    updateAccount(getAccountByUserName(userName).get.copy(
        password     = form.password,
        mailAddress  = form.mailAddress,
        isAdmin      = form.isAdmin,
        url          = form.url))
    
    redirect("/admin/users")
  })

  private def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getAccountByUserName(value).map { _ => "User already exists." }
  }  
  
}