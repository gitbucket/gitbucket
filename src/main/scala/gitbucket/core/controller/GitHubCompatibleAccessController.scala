package gitbucket.core.controller

import org.scalatra.MovedPermanently

class GitHubCompatibleAccessController extends GitHubCompatibleAccessControllerBase

/**
  * Provides GitHub compatible URLs for Git client.
  *
  * <ul>
  *   <li>git clone http://localhost:8080/owner/repo</li>
  *   <li>git clone http://localhost:8080/owner/repo.git</li>
  * </ul>
  */
trait GitHubCompatibleAccessControllerBase extends ControllerBase {
  /**
    * Git client initiates a connection with /info/refs
    *
    * @see https://git-scm.com/book/en/v2/Git-Internals-Transfer-Protocols
    */
  get("/*/*/info/refs") {
    redirectToGitServlet()
  }

  private def redirectToGitServlet(): Unit = {
    val query = Option(request.getQueryString).map("?" + _).getOrElse("")
    halt(MovedPermanently(baseUrl + "/git" + request.getRequestURI + query))
  }
}
