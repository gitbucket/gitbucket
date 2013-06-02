package app

import service._
import jp.sf.amateras.scalatra.forms._

class SignInController extends SignInControllerBase with AccountService

trait SignInControllerBase extends ControllerBase { self: AccountService =>
  
  case class SignInForm(email: String, password: String)
  
  val form = mapping(
    "email"    -> trim(label("Email",    text(required))), 
    "password" -> trim(label("Password", text(required)))
  )(SignInForm.apply)
  
  get("/signin"){
    html.signin()
  }

  post("/signin", form){ form =>
    val account = getAccountByUserName(form.email)
    if(account.isEmpty || account.get.password != form.password){
      redirect("/signin")
    } else {
      session.setAttribute("LOGIN_ACCOUNT", account.get)
      redirect("/%s".format(account.get.userName))
    }
  }

  get("/signout"){
    session.invalidate
    redirect("/signin")
  }

}