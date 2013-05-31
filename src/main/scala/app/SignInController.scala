package app

import jp.sf.amateras.scalatra.forms._

class SignInController extends ControllerBase {
  
  case class SignInForm(email: String, password: String)
  
  val form = mapping(
    "email"    -> trim(label("Email",    text(required))), 
    "password" -> trim(label("Password", text(required)))
  )(SignInForm.apply)
  
  get("/signin"){
    html.signin()
  }

  post("/signin", form){ form =>
    // TODO check email and password
    redirect("/%s".format(context.loginUser))
  }
}