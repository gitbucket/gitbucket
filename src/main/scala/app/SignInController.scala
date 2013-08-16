package app

import service._
import util.StringUtil._
import jp.sf.amateras.scalatra.forms._
import util.LDAPUtil
import service.SystemSettingsService.SystemSettings

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
    val settings = loadSystemSettings()
    if(settings.ldapAuthentication){
      ldapAuthentication(form, settings)
    } else {
      defaultAuthentication(form)
    }
  }

  get("/signout"){
    session.invalidate
    redirect("/")
  }

  /**
   * Authenticate by internal database.
   */
  private def defaultAuthentication(form: SignInForm) = {
    getAccountByUserName(form.userName).collect {
      case account if(!account.isGroupAccount && account.password == sha1(form.password)) => signin(account)
    } getOrElse redirect("/signin")
  }

  /**
   * Authenticate by LDAP.
   */
  private def ldapAuthentication(form: SignInForm, settings: SystemSettings) = {
    LDAPUtil.authenticate(settings.ldap.get, form.userName, form.password) match {
      case Right(mailAddress) => {
        // Create or update account by LDAP information
        getAccountByUserName(form.userName) match {
          case Some(x) => updateAccount(x.copy(mailAddress = mailAddress))
          case None    => createAccount(form.userName, "", mailAddress, false, None)
        }
        signin(getAccountByUserName(form.userName).get)
      }
      case Left(errorMessage) => defaultAuthentication(form)
    }
  }

  /**
   * Set account information into HttpSession and redirect.
   */
  private def signin(account: model.Account) = {
    session.setAttribute("LOGIN_ACCOUNT", account)
    updateLastLoginDate(account.userName)

    session.get("REDIRECT").map { redirectUrl =>
      session.removeAttribute("REDIRECT")
      redirect(redirectUrl.asInstanceOf[String])
    }.getOrElse {
      redirect("/")
    }
  }

}