package gitbucket.core.controller

import gitbucket.core.plugin.PluginRegistry
import org.scalatra.MovedPermanently

class PreProcessController extends PreProcessControllerBase

trait PreProcessControllerBase extends ControllerBase {

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
  get(!context.settings.allowAnonymousAccess, context.loginAccount.isEmpty) {
    if (!context.currentPath.startsWith("/assets") && !context.currentPath.startsWith("/signin") &&
        !context.currentPath.startsWith("/register") && !context.currentPath.endsWith("/info/refs") &&
        !PluginRegistry().getAnonymousAccessiblePaths().exists { path =>
          context.currentPath.startsWith(path)
        }) {
      Unauthorized()
    } else {
      pass()
    }
  }

}
