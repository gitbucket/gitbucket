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
    val redirect = params.get("redirect")
    if(redirect.isDefined && redirect.get.startsWith("/")){
      session.setAttribute("REDIRECT", redirect.get)
    }
    html.signin(loadSystemSettings())
  }

  post("/signin", form){ form =>
    authenticate(loadSystemSettings(), form.userName, form.password) match {
      case Some(account) => signin(account)
      case None => redirect("/signin")
    }
  }

  get("/signout"){
    session.invalidate
    redirect("/")
  }

  /**
   * Set account information into HttpSession and redirect.
   */
  private def signin(account: model.Account) = {
    session.setAttribute("LOGIN_ACCOUNT", account)
    updateLastLoginDate(account.userName)

    session.get("REDIRECT").map { case redirectUrl: String =>
      session.removeAttribute("REDIRECT")
      if(redirectUrl.replaceFirst("/$", "") == request.getContextPath){
        redirect("/")
      } else {
        redirect(redirectUrl)
      }
    }.getOrElse {
      redirect("/")
    }
  }

}