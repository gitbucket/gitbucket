package gitbucket.core.servlet

import javax.servlet._

import org.scalatra.ScalatraFilter

import scala.collection.mutable.ListBuffer

class CompositeScalatraFilter extends Filter {

  private val filters = new ListBuffer[(ScalatraFilter, String)]()

  def mount(filter: ScalatraFilter, path: String): Unit = {
    filters += ((filter, path))
  }

  override def init(filterConfig: FilterConfig): Unit = {
    filters.foreach { case (filter, _) =>
      filter.init(filterConfig)
    }
  }

  override def destroy(): Unit = {
    filters.foreach { case (filter, _) =>
      filter.destroy()
    }
  }

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    filters.foreach { case (filter, path) =>
      val mockChain = new MockFilterChain()
      filter.doFilter(request, response, mockChain)
      if(mockChain.continue == false){
        return ()
      }
    }
    chain.doFilter(request, response)
  }

}

class MockFilterChain extends FilterChain {

  var continue: Boolean = false

  override def doFilter(request: ServletRequest, response: ServletResponse): Unit = {
    continue = true
  }
}

