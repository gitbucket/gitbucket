package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import gitbucket.core.service.SystemSettingsService
import gitbucket.core.util.Implicits._

/**
  * A controller to provide GitHub compatible URL for Git clients.
  */
class GHCompatRepositoryAccessFilter extends Filter with SystemSettingsService {

  override def init(filterConfig: FilterConfig): Unit = {}

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val request = req.asInstanceOf[HttpServletRequest]
    request.paths match {
      // baseUrl/repositoryOwner/repositoryName/info/refs
      // baseUrl/repositoryOwner/repositoryName.git/info/refs
      case Array(repositoryOwner, repositoryName, "info", "refs", _*) => redirectToGitServlet(req, res)

      case _ => chain.doFilter(req, res)
    }
  }

  private def redirectToGitServlet(req: ServletRequest, res: ServletResponse): Unit = {
    val request = req.asInstanceOf[HttpServletRequest]
    val response = res.asInstanceOf[HttpServletResponse]
    val query = Option(request.getQueryString).map("?" + _).getOrElse("")
    response.sendRedirect(baseUrl(request) + "/git" + request.getRequestURI + query)
  }

  override def destroy(): Unit = {}

}
