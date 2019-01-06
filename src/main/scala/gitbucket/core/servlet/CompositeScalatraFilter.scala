package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.HttpServletRequest

import org.scalatra.ScalatraFilter

import scala.collection.mutable.ListBuffer

abstract class ControllerFilter extends Filter {

  def process(request: ServletRequest, response: ServletResponse, checkPath: String): Boolean

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val contextPath = request.getServletContext.getContextPath
    val requestPath = request.asInstanceOf[HttpServletRequest].getRequestURI.substring(contextPath.length)
    val checkPath = if (requestPath.endsWith("/")) {
      requestPath
    } else {
      requestPath + "/"
    }

    if (!checkPath.startsWith("/upload/") && !checkPath.startsWith("/git/") && !checkPath.startsWith("/git-lfs/") &&
        !checkPath.startsWith("/assets/") && !checkPath.startsWith("/plugin-assets/")) {
      val continue = process(request, response, checkPath)
      if (!continue) {
        return ()
      }
    }

    chain.doFilter(request, response)
  }
}

class CompositeScalatraFilter extends ControllerFilter {

  private val filters = new ListBuffer[(ScalatraFilter, String)]()

  def mount(filter: ScalatraFilter, path: String): Unit = {
    filters += ((filter, path))
  }

  override def init(filterConfig: FilterConfig): Unit = {
    filters.foreach {
      case (filter, _) =>
        filter.init(filterConfig)
    }
  }

  override def destroy(): Unit = {
    filters.foreach {
      case (filter, _) =>
        filter.destroy()
    }
  }

  override def process(request: ServletRequest, response: ServletResponse, checkPath: String): Boolean = {
    filters
      .filter {
        case (_, path) =>
          val start = path.replaceFirst("/\\*$", "/")
          checkPath.startsWith(start)
      }
      .foreach {
        case (filter, _) =>
          val mockChain = new MockFilterChain()
          filter.doFilter(request, response, mockChain)
          if (mockChain.continue == false) {
            return false
          }
      }

    true
  }

}

class MockFilterChain extends FilterChain {
  var continue: Boolean = false

  override def doFilter(request: ServletRequest, response: ServletResponse): Unit = {
    continue = true
  }
}

//class FilterChainFilter(chain: FilterChain) extends Filter {
//  override def init(filterConfig: FilterConfig): Unit = ()
//  override def destroy(): Unit  = ()
//  override def doFilter(request: ServletRequest, response: ServletResponse, mockChain: FilterChain) = chain.doFilter(request, response)
//}
