package app

import service._
import jp.sf.amateras.scalatra.forms._

class SignInController extends SignInControllerBase with SystemSettingsService with AccountService

trait SignInControllerBase extends ControllerBase { self: SystemSettingsService with AccountService =>
  
  case class SignInForm(userName: String, password: String)
  
  val form = mapping(
    "userName" -> trim(label("Username", text(required))),
    "password" -> trim(label("Password", text(required)))
  )(SignInForm.apply)
  
  get("/signin"){
    html.signin(loadSystemSettings())
  }

  post("/signin", form){ form =>
    val account = getAccountByUserName(form.userName)
    if(account.isEmpty || account.get.password != form.password){
      redirect("/signin")
    } else {
      session.setAttribute("LOGIN_ACCOUNT", account.get)
      updateLastLoginDate(account.get.userName)
      redirect("/%s".format(account.get.userName))
    }
  }

  get("/signout"){
    session.invalidate
    redirect("/")
  }

}