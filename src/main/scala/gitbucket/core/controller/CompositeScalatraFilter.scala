package gitbucket.core.controller

import javax.servlet._

import org.scalatra.ScalatraFilter

import scala.collection.mutable.ListBuffer

class CompositeScalatraFilter extends Filter {

  private val filters = new ListBuffer[ScalatraFilter]()

  def mount(filter: ScalatraFilter): Unit = {
    filters += filter
  }

  override def init(filterConfig: FilterConfig): Unit = {
    filters.foreach(_.init(filterConfig))
  }

  override def destroy(): Unit = {
    filters.foreach(_.destroy())
  }

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    filters.foreach { filter =>
      val mockChain = new MockFilterChain()
      filter.doFilter(request, response, mockChain)
      if(mockChain.continue == false){
        return ()
      }
    }
    chain.doFilter(request, response)
  }

  class MockFilterChain extends FilterChain {

    var continue: Boolean = false

    override def doFilter(request: ServletRequest, response: ServletResponse): Unit = {
      continue = true
    }
  }

}
