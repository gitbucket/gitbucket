package servlet

import javax.servlet._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.apache.commons.io.IOUtils
import twirl.api.Html
import service.SystemSettingsService
import model.Account
import util.Keys

class PluginActionInvokeFilter extends Filter with SystemSettingsService {

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    (req, res) match {
      case (request: HttpServletRequest, response: HttpServletResponse) => {
        val path = req.asInstanceOf[HttpServletRequest].getRequestURI
        val action = plugin.PluginSystem.actions.find(_.path == path)

        if(action.isDefined){
          val result = action.get.function(request, response)
          result match {
            case x: String => {
              response.setContentType("text/html; charset=UTF-8")
              val loginAccount = request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
              val context = app.Context(loadSystemSettings(), Option(loginAccount), request)
              val html = _root_.html.main("GitBucket", None)(Html(x))(context)
              IOUtils.write(html.toString.getBytes("UTF-8"), response.getOutputStream)
            }
            case x => {
              // TODO returns as JSON?
              response.setContentType("application/json; charset=UTF-8")

            }
          }
        } else {
          chain.doFilter(req, res)
        }
      }
    }
  }


}
