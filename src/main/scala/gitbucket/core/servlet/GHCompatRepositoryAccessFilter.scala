package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import gitbucket.core.service.SystemSettingsService

/**
  * A controller to provide GitHub compatible URL for Git clients.
  */
class GHCompatRepositoryAccessFilter extends Filter with SystemSettingsService {

  /**
    * Pattern of GitHub compatible repository URL.
    * <code>/:user/:repo.git/</code>
    */
  private val githubRepositoryPattern = """^/[^/]+/[^/]+\.git/.*""".r

  override def init(filterConfig: FilterConfig) = {}

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) = {
    implicit val request = req.asInstanceOf[HttpServletRequest]
    val agent = request.getHeader("USER-AGENT")
    val response = res.asInstanceOf[HttpServletResponse]
    val requestPath = request.getRequestURI.substring(request.getContextPath.length)
    val queryString = if (request.getQueryString != null) "?" + request.getQueryString else ""

    requestPath match {
      case githubRepositoryPattern() if agent != null && agent.toLowerCase.indexOf("git") >= 0 =>
        response.sendRedirect(baseUrl + "/git" + requestPath + queryString)
      case _ =>
        chain.doFilter(req, res)
    }
  }

  override def destroy() = {}

}
