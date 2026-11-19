package gitbucket.core.servlet

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.util.Try

/**
 * Rejects request paths with "." or ".." segments, also percent-encoded or after an encoded slash or a backslash.
 * Routes and filters read the owner and the repository name from the path as sent, while the servlet container
 * resolves such segments, so both must see the same path.
 */
class RequestPathFilter extends Filter {

  override def init(config: FilterConfig): Unit = {}

  override def destroy(): Unit = {}

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    if (RequestPathFilter.hasDotSegment(req.asInstanceOf[HttpServletRequest].getRequestURI)) {
      res.asInstanceOf[HttpServletResponse].sendError(HttpServletResponse.SC_BAD_REQUEST)
    } else {
      chain.doFilter(req, res)
    }
  }
}

object RequestPathFilter {

  def hasDotSegment(path: String): Boolean =
    Try(URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8))
      .map(_.split("[/\\\\]").exists(segment => segment == "." || segment == ".."))
      .getOrElse(true)
}
