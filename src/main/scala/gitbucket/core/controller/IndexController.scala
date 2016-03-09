package gitbucket.core.controller

import gitbucket.core.api._
import gitbucket.core.helper.xml
import gitbucket.core.model.Account
import gitbucket.core.service.{RepositoryService, ActivityService, AccountService, RepositorySearchService, IssuesService}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.{LDAPUtil, Keys, UsersAuthenticator, ReferrerAuthenticator, StringUtil}

import io.github.gitbucket.scalatra.forms._


class IndexController extends IndexControllerBase 
  with RepositoryService with ActivityService with AccountService with RepositorySearchService with IssuesService
  with UsersAuthenticator with ReferrerAuthenticator


trait IndexControllerBase extends ControllerBase {
  self: RepositoryService with ActivityService with AccountService with RepositorySearchService
    with UsersAuthenticator with ReferrerAuthenticator =>

  case class SignInForm(userName: String, password: String)

  val signinForm = mapping(
    "userName" -> trim(label("Username", text(required))),
    "password" -> trim(label("Password", text(required)))
  )(SignInForm.apply)

  val searchForm = mapping(
    "query"      -> trim(text(required)),
    "owner"      -> trim(text(required)),
    "repository" -> trim(text(required))
  )(SearchForm.apply)

  case class SearchForm(query: String, owner: String, repository: String)


  get("/"){
    val loginAccount = context.loginAccount
    if(loginAccount.isEmpty) {
        gitbucket.core.html.index(getRecentActivities(),
            getVisibleRepositories(loginAccount, withoutPhysicalInfo = true),
            loginAccount.map{ account => getUserRepositories(account.userName, withoutPhysicalInfo = true) }.getOrElse(Nil)
        )
    } else {
        val loginUserName = loginAccount.get.userName
        val loginUserGroups = getGroupsByUserName(loginUserName)
        var visibleOwnerSet : Set[String] = Set(loginUserName)
        
        visibleOwnerSet ++= loginUserGroups

        gitbucket.core.html.index(getRecentActivitiesByOwners(visibleOwnerSet),
            getVisibleRepositories(loginAccount, withoutPhysicalInfo = true),
            loginAccount.map{ account => getUserRepositories(account.userName, withoutPhysicalInfo = true) }.getOrElse(Nil) 
        )
    }
  }

  get("/signin"){
    val redirect = params.get("redirect")
    if(redirect.isDefined && redirect.get.startsWith("/")){
      flash += Keys.Flash.Redirect -> redirect.get
    }
    gitbucket.core.html.signin()
  }

  post("/signin", signinForm){ form =>
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
   * JSON API for checking user existence.
   */
  post("/_user/existence")(usersOnly {
    getAccountByUserName(params("userName")).isDefined
  })

  // TODO Move to RepositoryViwerController?
  post("/search", searchForm){ form =>
    redirect(s"/${form.owner}/${form.repository}/search?q=${StringUtil.urlEncode(form.query)}")
  }

  // TODO Move to RepositoryViwerController?
  get("/:owner/:repository/search")(referrersOnly { repository =>
    defining(params("q").trim, params.getOrElse("type", "code")){ case (query, target) =>
      val page   = try {
        val i = params.getOrElse("page", "1").toInt
        if(i <= 0) 1 else i
      } catch {
        case e: NumberFormatException => 1
      }

      target.toLowerCase match {
        case "issue" => gitbucket.core.search.html.issues(
          searchIssues(repository.owner, repository.name, query),
          countFiles(repository.owner, repository.name, query),
          query, page, repository)

        case _ => gitbucket.core.search.html.code(
          searchFiles(repository.owner, repository.name, query),
          countIssues(repository.owner, repository.name, query),
          query, page, repository)
      }
    }
  })
}
