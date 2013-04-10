import app._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new CreateRepositoryServlet, "/new")
    context.mount(new RepositoryViewerServlet, "/*")
  }
}