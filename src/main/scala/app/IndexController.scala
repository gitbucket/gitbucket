package app

import util._
import service._
import jp.sf.amateras.scalatra.forms._

class IndexController extends IndexControllerBase 
  with RepositoryService with ActivityService with AccountService with UsersAuthenticator

trait IndexControllerBase extends ControllerBase {
  self: RepositoryService with ActivityService with AccountService with UsersAuthenticator =>

  case class SignInForm(userName: String, password: String)

  val form = mapping(
    "userName" -> trim(label("Username", text(required))),
    "password" -> trim(label("Password", text(required)))
  )(SignInForm.apply)

  get("/"){
    val loginAccount = context.loginAccount

    html.index(getRecentActivities(),
      getVisibleRepositories(loginAccount, context.baseUrl),
      loginAccount.map{ account => getUserRepositories(account.userName, context.baseUrl) }.getOrElse(Nil)
    )
  }

  get("/signin"){
    val redirect = params.get("redirect")
    if(redirect.isDefined && redirect.get.startsWith("/")){
      flash += Keys.Flash.Redirect -> redirect.get
    }
    html.signin()
  }

  post("/signin", form){ form =>
    authenticate(context.settings, form.userName, form.password) match {
      case Some(account) => signin(account)
      case None          => redirect("/signin")
    }
  }

  get("/signout"){
    session.invalidate
    redirect("/")
  }

  get("/activities.atom"){
    contentType = "application/atom+xml; type=feed"
    helper.xml.feed(getRecentActivities())
  }

  /**
   * Set account information into HttpSession and redirect.
   */
  private def signin(account: model.Account) = {
    session.setAttribute(Keys.Session.LoginAccount, account)
    updateLastLoginDate(account.userName)

    flash.get(Keys.Flash.Redirect).asInstanceOf[Option[String]].map { redirectUrl =>
      if(redirectUrl.stripSuffix("/") == request.getContextPath){
        redirect("/")
      } else {
        redirect(redirectUrl)
      }
    }.getOrElse {
      redirect("/")
    }
  }

  /**
   * JSON API for collaborator completion.
   *
   * TODO Move to other controller?
   */
  get("/_user/proposals")(usersOnly {
    contentType = formats("json")
    org.json4s.jackson.Serialization.write(
      Map("options" -> getAllUsers().filter(!_.isGroupAccount).map(_.userName).toArray)
    )
  })


}
