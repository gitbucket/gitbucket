package servlet

import javax.servlet._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.apache.commons.io.IOUtils

class PluginActionInvokeFilter extends Filter {

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    (req, res) match {
      case (request: HttpServletRequest, response: HttpServletResponse) => {
        val path = req.asInstanceOf[HttpServletRequest].getRequestURI
        //println(req.asInstanceOf[HttpServletRequest].getContextPath)
        //println(req.asInstanceOf[HttpServletRequest].getRequestURL)

        val action = plugin.PluginSystem.actions.find(_.path == path)
        if(action.isDefined){
          val result = action.get.function(request, response)
          result match {
            case x: String => {
              response.setContentType("text/plain; charset=UTF-8")
              IOUtils.write(x.getBytes("UTF-8"), response.getOutputStream)
            }
            case x => {
              // TODO returns as JSON?
            }
          }
        } else {
          chain.doFilter(req, res)
        }
      }
    }
  }


}
