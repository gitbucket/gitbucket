package gitbucket.core.controller

import gitbucket.core.helper.xml
import gitbucket.core.model.Account
import gitbucket.core.service._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.{Keys, LDAPUtil, ReferrerAuthenticator, UsersAuthenticator}
import io.github.gitbucket.scalatra.forms._
import org.scalatra.Ok


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

//  val searchForm = mapping(
//    "query"      -> trim(text(required)),
//    "owner"      -> trim(text(required)),
//    "repository" -> trim(text(required))
//  )(SearchForm.apply)
//
//  case class SearchForm(query: String, owner: String, repository: String)


  get("/"){
    context.loginAccount.map { account =>
      val visibleOwnerSet: Set[String] = Set(account.userName) ++ getGroupsByUserName(account.userName)
      gitbucket.core.html.index(getRecentActivitiesByOwners(visibleOwnerSet), Nil, getUserRepositories(account.userName, withoutPhysicalInfo = true))
    }.getOrElse {
      gitbucket.core.html.index(getRecentActivities(), getVisibleRepositories(None, withoutPhysicalInfo = true), Nil)
    }
  }

  get("/signin"){
    val redirect = params.get("redirect")
    if(redirect.isDefined && redirect.get.startsWith("/")){
      flash += Keys.Flash.Redirect -> redirect.get
    }
    gitbucket.core.html.signin(flash.get("userName"), flash.get("password"), flash.get("error"))
  }

  post("/signin", signinForm){ form =>
    authenticate(context.settings, form.userName, form.password) match {
      case Some(account) => signin(account)
      case None          => {
        flash += "userName" -> form.userName
        flash += "password" -> form.password
        flash += "error" -> "Sorry, your Username and/or Password is incorrect. Please try again."
        redirect("/signin")
      }
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

  get("/sidebar-collapse"){
    if(params("collapse") == "true"){
      session.setAttribute("sidebar-collapse", "true")
    }  else {
      session.setAttribute("sidebar-collapse", null)
    }
    Ok()
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
    val user  = params("user").toBoolean
    val group = params("group").toBoolean
    val allUsers = getAllUsers(false)
      .withFilter { t => (user, group) match {
        case (true, true) => true
        case (true, false) => !t.isGroupAccount
        case (false, true) => t.isGroupAccount
        case (false, false) => false
      }}
    org.json4s.jackson.Serialization.write(
      Map("options" -> (
        allUsers.map { t => t.userName }
      ),
        "nameMap" -> allUsers.map{t => t.userName -> t.fullName}.toMap
      )
    )
  })

  /**
   * JSON API for checking user or group existence.
   * Returns a single string which is any of "group", "user" or "".
   */
  post("/_user/existence")(usersOnly {
    getAccountByUserName(params("userName")).map { account =>
      if(account.isGroupAccount) "group" else "user"
    } getOrElse ""
  })

  // TODO Move to RepositoryViwerController?
  get("/:owner/:repository/search")(referrersOnly { repository =>
    defining(params.getOrElse("q", "").trim, params.getOrElse("type", "code")){ case (query, target) =>
      val page = try {
        val i = params.getOrElse("page", "1").toInt
        if(i <= 0) 1 else i
      } catch {
        case e: NumberFormatException => 1
      }

      target.toLowerCase match {
        case "issue" => gitbucket.core.search.html.issues(
          if(query.nonEmpty) searchIssues(repository.owner, repository.name, query) else Nil,
          query, page, repository)

        case "wiki" => gitbucket.core.search.html.wiki(
          if(query.nonEmpty) searchWikiPages(repository.owner, repository.name, query) else Nil,
          query, page, repository)

        case _ => gitbucket.core.search.html.code(
          if(query.nonEmpty) searchFiles(repository.owner, repository.name, query) else Nil,
          query, page, repository)
      }
    }
  })

  get("/search"){
    val query = params.getOrElse("query", "").trim.toLowerCase
    val visibleRepositories = getVisibleRepositories(context.loginAccount, repositoryUserName = None, withoutPhysicalInfo = true)
    val repositories = visibleRepositories.filter { repository =>
      repository.name.toLowerCase.indexOf(query) >= 0 || repository.owner.toLowerCase.indexOf(query) >= 0
    }
    context.loginAccount.map { account =>
      gitbucket.core.search.html.repositories(query, repositories, Nil, getUserRepositories(account.userName, withoutPhysicalInfo = true))
    }.getOrElse {
      gitbucket.core.search.html.repositories(query, repositories, visibleRepositories, Nil)
    }
  }

}
