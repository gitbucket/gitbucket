package servlet

import javax.servlet._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import model.Account
import play.twirl.api.Html
import plugin.PluginRegistry
import service.SystemSettingsService
import util.Keys
import app.Context
import plugin.Results._
import plugin.Sessions._

class PluginActionFilter extends Filter with SystemSettingsService {

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = (req, res) match {
    case (req: HttpServletRequest, res: HttpServletResponse) => {
      val method = req.getMethod.toLowerCase
      val path = req.getRequestURI.substring(req.getContextPath.length)
      val registry = PluginRegistry()

      registry.getGlobalAction(method, path).map { action =>
        // Create Context
        val loginAccount = req.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
        implicit val context = Context(loadSystemSettings(), Option(loginAccount), req)
        sessions.set(Database.getSession(req))
        try {
          // Invoke global action
          action(req, res, context) match {
            // TODO to be type classes?
            case x: String =>
              res.setContentType("text/plain; charset=UTF-8")
              res.getWriter.write(x)
              res.getWriter.flush()
            case x: Html =>
              res.setContentType("text/html; charset=UTF-8")
              // TODO title of plugin action
              res.getWriter.write(html.main("TODO")(x).body)
              res.getWriter.flush()
            case Redirect(x) =>
              res.sendRedirect(x)
            case Fragment(x) =>
              res.getWriter.write(x.body)
              res.getWriter.flush()
          }
        } finally {
          sessions.remove()
        }
      }.getOrElse {
        chain.doFilter(req, res)
      }
    }
  }

}
