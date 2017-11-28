package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.HttpServletRequest

import gitbucket.core.controller.ControllerBase
import gitbucket.core.plugin.PluginRegistry

class PluginControllerFilter extends Filter {

  private var filterConfig: FilterConfig = null

  override def init(filterConfig: FilterConfig): Unit = {
    this.filterConfig = filterConfig
  }

  override def destroy(): Unit = {
    PluginRegistry().getControllers().foreach { case (controller, _) =>
      controller.destroy()
    }
  }

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val requestUri = request.asInstanceOf[HttpServletRequest].getRequestURI

    PluginRegistry().getControllers()
      .filter { case (_, path) =>
        val start = path.replaceFirst("/\\*$", "/")
        (requestUri + "/").startsWith(start)
      }
      .foreach { case (controller, _) =>
        controller match {
          case x: ControllerBase if(x.config == null) => x.init(filterConfig)
          case _ => ()
        }
        val mockChain = new MockFilterChain()
        controller.doFilter(request, response, mockChain)

        if(mockChain.continue == false){
          return ()
        }
      }

    chain.doFilter(request, response)
  }

}
