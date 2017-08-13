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
    val controller = PluginRegistry().getControllers().filter { case (_, path) =>
      val requestUri = request.asInstanceOf[HttpServletRequest].getRequestURI
      val start = path.replaceFirst("/\\*$", "/")
      path.endsWith("/*") && (requestUri + "/").startsWith(start)
    }

    val filterChainWrapper = controller.foldLeft(chain){ case (chain, (controller, _)) =>
      new FilterChainWrapper(controller, chain)
    }
    filterChainWrapper.doFilter(request, response)
  }

  class FilterChainWrapper(controller: ControllerBase, chain: FilterChain) extends FilterChain {
    override def doFilter(request: ServletRequest, response: ServletResponse): Unit = {
      if(controller.config == null){
        controller.init(filterConfig)
      }
      controller.doFilter(request, response, chain)
    }
  }

}
