package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.HttpServletRequest

import gitbucket.core.plugin.PluginRegistry

class PluginControllerFilter extends Filter {

  override def init(filterConfig: FilterConfig): Unit = {
    PluginRegistry().getControllers().foreach { case (controller, _) =>
      controller.init(filterConfig)
    }
  }

  override def destroy(): Unit = {
    PluginRegistry().getControllers().foreach { case (controller, _) =>
      controller.destroy()
    }
  }

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val controller = PluginRegistry().getControllers().find { case (_, path) =>
      val requestUri = request.asInstanceOf[HttpServletRequest].getRequestURI
      path.endsWith("/*") && requestUri.startsWith(path.replaceFirst("/\\*$", "/"))
    }

    controller.map { case (controller, _) =>
      controller.doFilter(request, response, chain)
    }.getOrElse{
      chain.doFilter(request, response)
    }
  }
}
