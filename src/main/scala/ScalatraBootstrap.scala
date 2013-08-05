import app._
import org.scalatra._
import javax.servlet._

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new IndexController, "/")
    context.mount(new SearchController, "/")
    context.mount(new FileUploadController, "/upload")
    context.mount(new SignInController, "/*")
    context.mount(new DashboardController, "/*")
    context.mount(new UserManagementController, "/*")
    context.mount(new SystemSettingsController, "/*")
    context.mount(new CreateRepositoryController, "/*")
    context.mount(new AccountController, "/*")
    context.mount(new RepositoryViewerController, "/*")
    context.mount(new WikiController, "/*")
    context.mount(new LabelsController, "/*")
    context.mount(new MilestonesController, "/*")
    context.mount(new IssuesController, "/*")
    context.mount(new PullRequestsController, "/*")
    context.mount(new RepositorySettingsController, "/*")

    val dir = new java.io.File(_root_.util.Directory.GitBucketHome)
    if(!dir.exists){
      dir.mkdirs()
    }
  }
}