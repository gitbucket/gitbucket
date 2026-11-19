package gitbucket.core.controller

import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.scalatra.CsrfTokenSupport
import org.scalatra.{GenerateId, MovedPermanently}
import org.slf4j.LoggerFactory

import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse}

class PreProcessController extends PreProcessControllerBase

/**
 * Runs before all other controllers, plugin controllers and the upload servlet (the first filter, mounted on all
 * paths), so the CSRF token check applies to all of them.
 *
 * The check is `gitbucket.core.scalatra.CsrfTokenSupport`, a backport of Scalatra PR #2085 (see its Scaladoc for the
 * differences from Scalatra 3.2.1): an unsafe request with cookies needs the session's token. GitBucket's own rules
 * are here:
 *
 *  - Requests with an `Authorization` header are not checked.
 *  - A sign-in needs the token also without cookies (login CSRF).
 *  - The token is also accepted in the `X-XSRF-TOKEN` header, with the value of the `XSRF-TOKEN` cookie (the names
 *    axios and Angular use by default); the cookie is set here and in `Context.csrfToken`.
 *  - A rejected request gets 403 and a warning in the log.
 *
 * Templates read the token with `Context.csrfToken` (the same session attribute as the trait's `csrfToken`).
 *
 * See also `doc/csrf.md`.
 */
trait PreProcessControllerBase extends ControllerBase with CsrfTokenSupport {

  private val logger = LoggerFactory.getLogger(classOf[PreProcessController])

  override protected def csrfHeaderNames: Seq[String] = CsrfTokenSupport.HeaderNames :+ "X-XSRF-TOKEN"

  before() {
    // for tokens created later in this request, see Context.csrfToken
    request.setAttribute(CsrfCookie.ResponseAttribute, response)
    PreProcessControllerBase.existingCsrfToken(request).foreach(CsrfCookie.update(request, response, _))
  }

  /**
   * Requests with an `Authorization` header (API clients, git) don't use the session and are not checked. A sign-in
   * needs the token from the sign-in page also without cookies, against signing in a user with someone else's account
   * (login CSRF); with cookies, the trait's check already requires it.
   */
  override protected def isForged: Boolean =
    request.getHeader("Authorization") == null && (super.isForged || isSigninWithoutCookie)

  private def isSigninWithoutCookie: Boolean =
    request.getMethod == "POST" &&
      PreProcessControllerBase.SigninPaths.contains(request.getRequestURI.stripPrefix(request.getContextPath)) &&
      request.getHeader("Cookie") == null

  override protected def handleForgery(): Unit = {
    val user = context.loginAccount.map(_.userName).getOrElse("anonymous")
    logger.warn(s"Rejected ${request.getMethod} ${request.getRequestURI} of $user: no valid CSRF token")
    halt(403, "The request has no valid CSRF token. Reload the page and try again.")
  }

  /**
   * Provides GitHub compatible URLs (e.g. http://localhost:8080/owner/repo.git) for Git client.
   */
  get("/*/*/info/refs") {
    val query = Option(request.getQueryString).map("?" + _).getOrElse("")
    halt(MovedPermanently(baseUrl + "/git" + request.getRequestURI + query))
  }

  /**
   * Provides GitHub compatible URLs for GitLFS client.
   */
  post("/*/*/info/lfs/objects/batch") {
    val dispatcher = request.getRequestDispatcher("/git" + request.getRequestURI)
    dispatcher.forward(request, response)
  }

  /**
   * Filter requests from anonymous users.
   *
   * If anonymous access is allowed, pass all requests.
   * But if it's not allowed, demands authentication except some paths.
   */
  get(!context.settings.basicBehavior.allowAnonymousAccess, context.loginAccount.isEmpty) {
    if (
      !context.currentPath.startsWith("/assets") && !context.currentPath.startsWith("/signin") &&
      !context.currentPath.startsWith("/register") && !context.currentPath.endsWith("/info/refs") &&
      !context.currentPath.startsWith("/plugin-assets") && !context.currentPath.equals("/user.css") &&
      !PluginRegistry().getAnonymousAccessiblePaths().exists { path =>
        context.currentPath.startsWith(path)
      }
    ) {
      Unauthorized()
    } else {
      pass()
    }
  }

}

object PreProcessControllerBase {
  private val SigninPaths = Set("/signin", "/signin/oidc")

  /** The session's CSRF token. Creates neither a session nor a token. */
  private[core] def existingCsrfToken(request: HttpServletRequest): Option[String] =
    Option(request.getSession(false))
      .flatMap(session => Option(session.getAttribute(CsrfTokenSupport.DefaultKey)))
      .map(_.toString)

  /**
   * The session's CSRF token, stored like `CsrfTokenSupport.csrfToken` does; creates the session, the token and its
   * cookie if needed.
   */
  private[core] def csrfToken(request: HttpServletRequest): String =
    existingCsrfToken(request).getOrElse {
      val token = GenerateId()
      request.getSession.setAttribute(CsrfTokenSupport.DefaultKey, token)
      CsrfCookie.update(request, token)
      token
    }
}

/**
 * The `XSRF-TOKEN` cookie: the session's CSRF token for scripts that read it from a cookie (axios, Angular) and for
 * pages without the core layout.
 */
object CsrfCookie {

  val Name = "XSRF-TOKEN"

  private[core] val ResponseAttribute = "gitbucket.core.controller.CsrfCookie.response"

  /** Sets the cookie if the request doesn't have it with this value. */
  def update(request: HttpServletRequest, response: HttpServletResponse, token: String): Unit = {
    val current = Option(request.getCookies).flatMap(_.find(_.getName == Name)).map(_.getValue)
    if (!current.contains(token) && !response.isCommitted) {
      val cookie = new Cookie(Name, token)
      // same path as the session cookie; readable by scripts
      cookie.setPath(if (request.getContextPath.isEmpty) "/" else request.getContextPath)
      cookie.setSecure(request.getServletContext.getSessionCookieConfig.isSecure)
      response.addCookie(cookie)
    }
  }

  /** Sets the cookie for a token created after [[PreProcessControllerBase]] ran, e.g. while rendering a page. */
  def update(request: HttpServletRequest, token: String): Unit =
    Option(request.getAttribute(ResponseAttribute)).collect { case response: HttpServletResponse =>
      update(request, response, token)
    }
}
