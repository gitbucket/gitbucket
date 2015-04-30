package gitbucket.core.controller

import gitbucket.core.api._
import gitbucket.core.helper.xml
import gitbucket.core.html
import gitbucket.core.model.Account
import gitbucket.core.service.{RepositoryService, ActivityService, AccountService}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{LDAPUtil, Keys, UsersAuthenticator}

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
    if(loginAccount.isEmpty) {
        html.index(getRecentActivities(),
            getVisibleRepositories(loginAccount, context.baseUrl, withoutPhysicalInfo = true),
            loginAccount.map{ account => getUserRepositories(account.userName, context.baseUrl, withoutPhysicalInfo = true) }.getOrElse(Nil)
        )
    } else {
        val loginUserName = loginAccount.get.userName
        val loginUserGroups = getGroupsByUserName(loginUserName)
        var visibleOwnerSet : Set[String] = Set(loginUserName)
        
        visibleOwnerSet ++= loginUserGroups

        html.index(getRecentActivitiesByOwners(visibleOwnerSet),
            getVisibleRepositories(loginAccount, context.baseUrl, withoutPhysicalInfo = true),
            loginAccount.map{ account => getUserRepositories(account.userName, context.baseUrl, withoutPhysicalInfo = true) }.getOrElse(Nil) 
        )
    }
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
    xml.feed(getRecentActivities())
  }

  /**
   * Set account information into HttpSession and redirect.
   */
  private def signin(account: Account) = {
    session.setAttribute(Keys.Session.LoginAccount, account)
    updateLastLoginDate(account.userName)

    if(LDAPUtil.isDummyMailAddress(account)) {
      redirect("/" + account.userName + "/_edit")
    }

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
   */
  get("/_user/proposals")(usersOnly {
    contentType = formats("json")
    org.json4s.jackson.Serialization.write(
      Map("options" -> getAllUsers(false).filter(!_.isGroupAccount).map(_.userName).toArray)
    )
  })

  /**
   * JSON APU for checking user existence.
   */
  post("/_user/existence")(usersOnly {
    getAccountByUserName(params("userName")).isDefined
  })

  /**
   * @see https://developer.github.com/v3/rate_limit/#get-your-current-rate-limit-status
   * but not enabled.
   */
  get("/api/v3/rate_limit"){
    contentType = formats("json")
    // this message is same as github enterprise...
    org.scalatra.NotFound(ApiError("Rate limiting is not enabled."))
  }
}
