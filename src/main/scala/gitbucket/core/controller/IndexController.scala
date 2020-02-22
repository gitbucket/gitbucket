package gitbucket.core.controller

import java.net.URI

import com.nimbusds.oauth2.sdk.id.State
import com.nimbusds.openid.connect.sdk.Nonce
import gitbucket.core.helper.xml
import gitbucket.core.model.Account
import gitbucket.core.service._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util._
import gitbucket.core.view.helpers._
import org.scalatra.Ok
import org.scalatra.forms._

class IndexController
    extends IndexControllerBase
    with RepositoryService
    with ActivityService
    with AccountService
    with RepositorySearchService
    with IssuesService
    with LabelsService
    with MilestonesService
    with PrioritiesService
    with UsersAuthenticator
    with ReferrerAuthenticator
    with AccessTokenService
    with AccountFederationService
    with OpenIDConnectService

trait IndexControllerBase extends ControllerBase {
  self: RepositoryService
    with ActivityService
    with AccountService
    with RepositorySearchService
    with UsersAuthenticator
    with ReferrerAuthenticator
    with AccessTokenService
    with AccountFederationService
    with OpenIDConnectService =>

  case class SignInForm(userName: String, password: String, hash: Option[String])

  val signinForm = mapping(
    "userName" -> trim(label("Username", text(required))),
    "password" -> trim(label("Password", text(required))),
    "hash" -> trim(optional(text()))
  )(SignInForm.apply)

//  val searchForm = mapping(
//    "query"      -> trim(text(required)),
//    "owner"      -> trim(text(required)),
//    "repository" -> trim(text(required))
//  )(SearchForm.apply)
//
//  case class SearchForm(query: String, owner: String, repository: String)

  case class OidcContext(state: State, nonce: Nonce, redirectBackURI: String)

  get("/") {
    context.loginAccount
      .map { account =>
        val visibleOwnerSet: Set[String] = Set(account.userName) ++ getGroupsByUserName(account.userName)
        gitbucket.core.html.index(
          getRecentActivitiesByOwners(visibleOwnerSet),
          getVisibleRepositories(
            Some(account),
            None,
            withoutPhysicalInfo = true,
            limit = context.settings.limitVisibleRepositories
          ),
          showBannerToCreatePersonalAccessToken = hasAccountFederation(account.userName) && !hasAccessToken(
            account.userName
          )
        )
      }
      .getOrElse {
        gitbucket.core.html.index(
          getRecentActivities(),
          getVisibleRepositories(None, withoutPhysicalInfo = true),
          showBannerToCreatePersonalAccessToken = false
        )
      }
  }

  get("/signin") {
    val redirect = params.get("redirect")
    if (redirect.isDefined && redirect.get.startsWith("/")) {
      flash.update(Keys.Flash.Redirect, redirect.get)
    }
    gitbucket.core.html.signin(flash.get("userName"), flash.get("password"), flash.get("error"))
  }

  post("/signin", signinForm) { form =>
    authenticate(context.settings, form.userName, form.password) match {
      case Some(account) =>
        flash.get(Keys.Flash.Redirect) match {
          case Some(redirectUrl: String) => signin(account, redirectUrl + form.hash.getOrElse(""))
          case _                         => signin(account)
        }
      case None =>
        flash.update("userName", form.userName)
        flash.update("password", form.password)
        flash.update("error", "Sorry, your Username and/or Password is incorrect. Please try again.")
        redirect("/signin")
    }
  }

  /**
   * Initiate an OpenID Connect authentication request.
   */
  post("/signin/oidc") {
    context.settings.oidc.map { oidc =>
      val redirectURI = new URI(s"$baseUrl/signin/oidc")
      val authenticationRequest = createOIDCAuthenticationRequest(oidc.issuer, oidc.clientID, redirectURI)
      val redirectBackURI = flash.get(Keys.Flash.Redirect) match {
        case Some(redirectBackURI: String) => redirectBackURI + params.getOrElse("hash", "")
        case _                             => "/"
      }
      session.setAttribute(
        Keys.Session.OidcContext,
        OidcContext(authenticationRequest.getState, authenticationRequest.getNonce, redirectBackURI)
      )
      redirect(authenticationRequest.toURI.toString)
    } getOrElse {
      NotFound()
    }
  }

  /**
   * Handle an OpenID Connect authentication response.
   */
  get("/signin/oidc") {
    context.settings.oidc.map { oidc =>
      val redirectURI = new URI(s"$baseUrl/signin/oidc")
      session.get(Keys.Session.OidcContext) match {
        case Some(context: OidcContext) =>
          authenticate(params.toMap, redirectURI, context.state, context.nonce, oidc).map { account =>
            signin(account, context.redirectBackURI)
          } orElse {
            flash.update("error", "Sorry, authentication failed. Please try again.")
            session.invalidate()
            redirect("/signin")
          }
        case _ =>
          flash.update("error", "Sorry, something wrong. Please try again.")
          session.invalidate()
          redirect("/signin")
      }
    } getOrElse {
      NotFound()
    }
  }

  get("/signout") {
    session.invalidate
    redirect("/")
  }

  get("/activities.atom") {
    contentType = "application/atom+xml; type=feed"
    xml.feed(getRecentActivities())
  }

  post("/sidebar-collapse") {
    if (params("collapse") == "true") {
      session.setAttribute("sidebar-collapse", "true")
    } else {
      session.setAttribute("sidebar-collapse", null)
    }
    Ok()
  }

  /**
   * Set account information into HttpSession and redirect.
   */
  private def signin(account: Account, redirectUrl: String = "/") = {
    session.setAttribute(Keys.Session.LoginAccount, account)
    updateLastLoginDate(account.userName)

    if (LDAPUtil.isDummyMailAddress(account)) {
      redirect("/" + account.userName + "/_edit")
    }

    if (redirectUrl.stripSuffix("/") == request.getContextPath) {
      redirect("/")
    } else {
      redirect(redirectUrl)
    }
  }

  /**
   * JSON API for collaborator completion.
   */
  get("/_user/proposals")(usersOnly {
    contentType = formats("json")
    val user = params("user").toBoolean
    val group = params("group").toBoolean
    org.json4s.jackson.Serialization.write(
      Map(
        "options" -> (
          getAllUsers(false)
            .withFilter { t =>
              (user, group) match {
                case (true, true)   => true
                case (true, false)  => !t.isGroupAccount
                case (false, true)  => t.isGroupAccount
                case (false, false) => false
              }
            }
            .map { t =>
              Map(
                "label" -> s"${avatar(t.userName, 16)}<b>@${StringUtil.escapeHtml(t.userName)}</b> ${StringUtil
                  .escapeHtml(t.fullName)}",
                "value" -> t.userName
              )
            }
        )
      )
    )
  })

  /**
   * JSON API for checking user or group existence.
   * Returns a single string which is any of "group", "user" or "".
   */
  post("/_user/existence")(usersOnly {
    getAccountByUserNameIgnoreCase(params("userName")).map { account =>
      if (account.isGroupAccount) "group" else "user"
    } getOrElse ""
  })

  // TODO Move to RepositoryViewrController?
  get("/:owner/:repository/search")(referrersOnly { repository =>
    defining(params.getOrElse("q", "").trim, params.getOrElse("type", "code")) {
      case (query, target) =>
        val page = try {
          val i = params.getOrElse("page", "1").toInt
          if (i <= 0) 1 else i
        } catch {
          case e: NumberFormatException => 1
        }

        target.toLowerCase match {
          case "issues" =>
            gitbucket.core.search.html.issues(
              if (query.nonEmpty) searchIssues(repository.owner, repository.name, query, false) else Nil,
              false,
              query,
              page,
              repository
            )

          case "pulls" =>
            gitbucket.core.search.html.issues(
              if (query.nonEmpty) searchIssues(repository.owner, repository.name, query, true) else Nil,
              true,
              query,
              page,
              repository
            )

          case "wiki" =>
            gitbucket.core.search.html.wiki(
              if (query.nonEmpty) searchWikiPages(repository.owner, repository.name, query) else Nil,
              query,
              page,
              repository
            )

          case _ =>
            gitbucket.core.search.html.code(
              if (query.nonEmpty) searchFiles(repository.owner, repository.name, query) else Nil,
              query,
              page,
              repository
            )
        }
    }
  })

  get("/search") {
    val query = params.getOrElse("query", "").trim.toLowerCase
    val visibleRepositories =
      getVisibleRepositories(
        context.loginAccount,
        None,
        withoutPhysicalInfo = true,
        limit = context.settings.limitVisibleRepositories
      )

    val repositories = {
      context.settings.limitVisibleRepositories match {
        case true =>
          getVisibleRepositories(
            context.loginAccount,
            None,
            withoutPhysicalInfo = true,
            limit = false
          )
        case false => visibleRepositories
      }
    }.filter { repository =>
      repository.name.toLowerCase.indexOf(query) >= 0 || repository.owner.toLowerCase.indexOf(query) >= 0
    }

    gitbucket.core.search.html.repositories(query, repositories, visibleRepositories)
  }
}
