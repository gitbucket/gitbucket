package servlet

import javax.servlet._
import javax.servlet.http._

class ContentTypeFilter extends Filter {
  def init(config: FilterConfig) { }

  def destroy() { }

  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val httpRequest  = request.asInstanceOf[HttpServletRequest]
    val httpResponse = response.asInstanceOf[HttpServletResponse]
    val context      = httpRequest.getContextPath
    val path         = httpRequest.getRequestURI.substring(context.length)

    if (!(path.startsWith("/assets/") || path.startsWith("/git/"))) {
      httpResponse.setContentType("text/html;charset=UTF-8")
    }

    chain.doFilter(request, response)
  }
}
