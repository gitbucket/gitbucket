package servlet

import javax.servlet._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import play.twirl.api.Html
import plugin.PluginRegistry

class PluginActionFilter extends Filter {

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = (req, res) match {
    case (req: HttpServletRequest, res: HttpServletResponse) => {
      val method = req.getMethod.toLowerCase
      val path = req.getRequestURI.substring(req.getContextPath.length)
      val registry = PluginRegistry()
      registry.getGlobalAction(method, path).map { action =>
        action(req, res) match {
          // TODO to be type classes?
          case x: String =>
            res.setContentType("text/plain; charset=UTF-8")
            res.getWriter.write(x)
            res.getWriter.flush()
          case x: Html =>
            res.setContentType("text/html; charset=UTF-8")
            res.getWriter.write(x.body)
            res.getWriter.flush()
        }
      }.getOrElse {
        chain.doFilter(req, res)
      }
    }
  }

}