import _root_.servlet.{BasicAuthenticationFilter, Database, TransactionFilter}
import app._
import jp.sf.amateras.scalatra.forms.ValidationJavaScriptProvider
import org.scalatra._
import javax.servlet._
import java.util.EnumSet

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    // Retrieve Slick driver
    val driver = Database.driver(context)

    // Register TransactionFilter and BasicAuthenticationFilter at first
    context.addFilter("transactionFilter", new TransactionFilter)
    context.getFilterRegistration("transactionFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/*")
    context.addFilter("basicAuthenticationFilter", new BasicAuthenticationFilter(driver))
    context.getFilterRegistration("basicAuthenticationFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/git/*")

    // Register controllers
    context.mount(new IndexController(driver), "/")
    context.mount(new SearchController(driver), "/")
    context.mount(new FileUploadController, "/upload")
    context.mount(new DashboardController(driver), "/*")
    context.mount(new UserManagementController(driver), "/*")
    context.mount(new SystemSettingsController(driver), "/*")
    context.mount(new AccountController(driver), "/*")
    context.mount(new RepositoryViewerController(driver), "/*")
    context.mount(new WikiController(driver), "/*")
    context.mount(new LabelsController(driver), "/*")
    context.mount(new MilestonesController(driver), "/*")
    context.mount(new IssuesController(driver), "/*")
    context.mount(new PullRequestsController(driver), "/*")
    context.mount(new RepositorySettingsController(driver), "/*")
    context.mount(new ValidationJavaScriptProvider, "/assets/common/js/*")

    // Create GITBUCKET_HOME directory if it does not exist
    val dir = new java.io.File(_root_.util.Directory.GitBucketHome)
    if(!dir.exists){
      dir.mkdirs()
    }
  }
}