import app._
import org.scalatra._
import javax.servlet._

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new IndexController, "/")
    context.mount(new SignInController, "/*")
    context.mount(new UsersController, "/*")
    context.mount(new CreateRepositoryController, "/*")
    context.mount(new AccountController, "/*")
    context.mount(new RepositoryViewerController, "/*")
    context.mount(new WikiController, "/*")
    context.mount(new LabelsController, "/*")
    context.mount(new MilestonesController, "/*")
    context.mount(new IssuesController, "/*")
    context.mount(new SettingsController, "/*")
    
    context.addListener(new ServletContextListener(){
      def contextInitialized(e: ServletContextEvent): Unit = {
        val dir = new java.io.File(_root_.util.Directory.GitBucketHome)
        if(!dir.exists){
          dir.mkdirs()
        }
      }
      
      def contextDestroyed(e: ServletContextEvent): Unit = {}
    })
  }
}