package gitbucket.core.controller

import org.scalatra.MovedPermanently

class PreProcessController extends PreProcessControllerBase

trait PreProcessControllerBase extends ControllerBase {

  /**
   * Provides GitHub compatible URLs for Git client.
   *
   * <ul>
   *   <li>git clone http://localhost:8080/owner/repo</li>
   *   <li>git clone http://localhost:8080/owner/repo.git</li>
   * </ul>
   *
   * @see https://git-scm.com/book/en/v2/Git-Internals-Transfer-Protocols
   */
  get("/*/*/info/refs") {
    val query = Option(request.getQueryString).map("?" + _).getOrElse("")
    halt(MovedPermanently(baseUrl + "/git" + request.getRequestURI + query))
  }

  /**
   * Filter requests from anonymous users.
   *
   * If anonymous access is allowed, pass all requests.
   * But if it's not allowed, demands authentication except some paths.
   */
  get(!context.settings.allowAnonymousAccess, context.loginAccount.isEmpty) {
    if(!context.currentPath.startsWith("/assets") && !context.currentPath.startsWith("/signin") &&
      !context.currentPath.startsWith("/register") && !context.currentPath.endsWith("/info/refs")) {
      Unauthorized()
    } else {
      pass()
    }
  }


}
