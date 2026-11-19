package gitbucket.core.scalatra

import org.scalatra.{GenerateId, ScalatraBase}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.servlet.http.HttpServletRequest

/**
 * Backport of Scalatra PR scalatra/scalatra#2085 (issue #2084, "Create the CSRF token on first use instead of on every
 * request", commit `e26f8ae5`) to Scalatra 3.2.1 (BSD-licensed). The same code as `org.scalatra.CsrfTokenSupport` in
 * the PR, only in this package and without the unchanged `GenerateId` and `XsrfTokenSupport`. Keep it in sync with the
 * PR until a Scalatra release has it.
 *
 * Differences from Scalatra 3.2.1, in two groups:
 *
 * Sessions (performance): the CSRF check never creates an HTTP session. Scalatra 3.2.1 prepares the token before every
 * request, which creates a session for every request (API clients, anonymous visitors, crawlers, static files).
 *
 *  - The token is created when it is first read (`csrfToken`); `csrfEagerToken = true` restores the old behaviour.
 *  - `isForged` doesn't create a session.
 *
 * Security (stricter check):
 *
 *  - An unsafe request is checked when it carries one of `csrfCredentialHeaders` (default `Cookie`), the credentials a
 *    browser sends by itself. It then needs the session's token; a request with cookies but without a session or
 *    without a token in the session is refused (Scalatra 3.2.1 lets it pass). A request without cookies is not checked:
 *    it carries no session credential.
 *  - Empty tokens don't match; tokens are compared in constant time.
 *  - `resetCsrfToken()` / [[CsrfTokenSupport.resetToken]] remove the token, e.g. at sign-in, so a token known before
 *    the sign-in is not valid after it.
 *  - The header names can be overridden (`csrfHeaderNames`).
 *
 * Effect in GitBucket: the security part applies now. The session part has no effect yet, because other code creates
 * a session for every request anyway (`Context.sidebarCollapse`, `ControllerBase.LoginAccount`,
 * `ApiAuthenticationFilter`, Scalatra's `FlashMapSupport`); it is needed so that fixing those removes the sessions.
 * Without it, Scalatra's trait alone would still create one for every request.
 *
 * Remove when a Scalatra release has the change (`ScalatraCsrfTokenSupportCanarySpec` fails then): use
 * `org.scalatra.CsrfTokenSupport` in `PreProcessControllerBase`, `IndexController` and the templates, then delete this
 * file, `CsrfTokenSupportSpec` and the canary spec. If the merged change differs from the PR, compare first.
 */
object CsrfTokenSupport {

  val DefaultKey = "org.scalatra.CsrfTokenSupport.key"

  val HeaderNames = Vector("X-CSRF-TOKEN")

  /**
   * Request headers with credentials that a browser sends by itself, also with a cross-site request. An unsafe request
   * with one of them must carry the token.
   */
  val CredentialHeaders = Vector("Cookie")

  /**
   * Removes the token from the request's session, if there is one; the next read creates a new token. For code that
   * doesn't mix in [[CsrfTokenSupport]], e.g. a sign-in servlet next to a filter with the check.
   */
  def resetToken(request: HttpServletRequest, key: String = DefaultKey): Unit =
    Option(request.getSession(false)).foreach(_.removeAttribute(key))

  private[scalatra] def tokenMatches(expected: String, actual: String): Boolean =
    actual.nonEmpty &&
      MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))

}

/**
 * Provides cross-site request forgery protection.
 *
 * The token is stored in the session. It is created when it is first read with `csrfToken`, e.g. when a page renders a
 * form, so requests that don't use it get no session. Override `csrfEagerToken` to create it before every request.
 *
 * Adds a before filter. An unsafe request that carries credentials a browser sends by itself (a `Cookie` header, see
 * `csrfCredentialHeaders`) must send the session's token in the request parameter `csrfKey` or in one of the
 * `csrfHeaderNames` headers; otherwise the `handleForgery()` hook is invoked. Requests without such credentials, e.g.
 * from API clients, are not checked.
 *
 * Call `resetCsrfToken()` when a user signs in, so that a token known before the sign-in is not valid after it.
 */
trait CsrfTokenSupport { this: ScalatraBase =>

  before(isForged) { handleForgery() }
  before(csrfEagerToken) { prepareCsrfToken() }

  /**
   * Tests whether a request with an unsafe method is a potential cross-site forgery. Creates no session.
   *
   * @return
   *   true if the request is an unsafe method (POST, PUT, DELETE, TRACE, CONNECT, PATCH), has one of the
   *   `csrfCredentialHeaders`, and neither the request parameter at `csrfKey` nor one of the `csrfHeaderNames` headers
   *   matches the session's token. A request without a session or without a token in the session matches nothing.
   */
  protected def isForged: Boolean =
    !request.requestMethod.isSafe &&
      csrfCredentialHeaders.exists(request.headers.get(_).isDefined) &&
      !existingCsrfToken.exists { token =>
        (params.get(csrfKey) ++ csrfHeaderNames.flatMap(request.headers.get))
          .exists(CsrfTokenSupport.tokenMatches(token, _))
      }

  /**
   * Take an action when a forgery is detected. The default action halts further request processing and returns a 403
   * HTTP status code.
   */
  protected def handleForgery(): Unit = {
    halt(403, "Request tampering detected!")
  }

  /**
   * Prepares a CSRF token: returns the session's token, or creates a token with `GenerateId` and stores it on the
   * session, creating the session if needed. Called by `csrfToken` if the session has no token, and before every
   * request if `csrfEagerToken` is true.
   */
  protected def prepareCsrfToken(): String = {
    session.getOrElseUpdate(csrfKey, GenerateId()).toString
  }

  /**
   * The key used to store the token on the session, as well as the parameter of the request.
   */
  def csrfKey: String = CsrfTokenSupport.DefaultKey

  /**
   * Whether the token is prepared before every request, which creates a session for every request (the behaviour of
   * earlier versions). By default the token is created when `csrfToken` is first read.
   */
  protected def csrfEagerToken: Boolean = false

  /**
   * The request headers that make an unsafe request subject to the check. Add `Authorization` if browsers send Basic
   * authentication to the application.
   */
  protected def csrfCredentialHeaders: Seq[String] = CsrfTokenSupport.CredentialHeaders

  /**
   * The request headers that may carry the token.
   */
  protected def csrfHeaderNames: Seq[String] = CsrfTokenSupport.HeaderNames

  /**
   * Returns the token from the session. Creates the token, and the session, if needed (see `prepareCsrfToken`).
   */
  def csrfToken(implicit request: HttpServletRequest): String =
    existingCsrfToken(request).getOrElse(prepareCsrfToken())

  /**
   * Removes the token from the session; the next `csrfToken` creates a new one. Call it when a user signs in.
   */
  def resetCsrfToken()(implicit request: HttpServletRequest): Unit =
    CsrfTokenSupport.resetToken(request, csrfKey)

  private def existingCsrfToken(implicit request: HttpServletRequest): Option[String] =
    sessionOption.flatMap(session => Option(session.getAttribute(csrfKey))).map(_.toString)

}
