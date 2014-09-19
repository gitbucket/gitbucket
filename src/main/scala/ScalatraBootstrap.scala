import _root_.servlet.{PluginActionInvokeFilter, BasicAuthenticationFilter, TransactionFilter}
import app._
//import jp.sf.amateras.scalatra.forms.ValidationJavaScriptProvider
import org.scalatra._
import javax.servlet._
import java.util.EnumSet

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    // Register TransactionFilter and BasicAuthenticationFilter at first
    context.addFilter("transactionFilter", new TransactionFilter)
    context.getFilterRegistration("transactionFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/*")
    context.addFilter("pluginActionInvokeFilter", new PluginActionInvokeFilter)
    context.getFilterRegistration("pluginActionInvokeFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/*")
    context.addFilter("basicAuthenticationFilter", new BasicAuthenticationFilter)
    context.getFilterRegistration("basicAuthenticationFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/git/*")

    // Register controllers
    context.mount(new IndexController, "/")
    context.mount(new SearchController, "/")
    context.mount(new FileUploadController, "/upload")
    context.mount(new DashboardController, "/*")
    context.mount(new UserManagementController, "/*")
    context.mount(new SystemSettingsController, "/*")
    context.mount(new AccountController, "/*")
    context.mount(new RepositoryViewerController, "/*")
    context.mount(new WikiController, "/*")
    context.mount(new LabelsController, "/*")
    context.mount(new MilestonesController, "/*")
    context.mount(new IssuesController, "/*")
    context.mount(new PullRequestsController, "/*")
    context.mount(new RepositorySettingsController, "/*")

    // Create GITBUCKET_HOME directory if it does not exist
    val dir = new java.io.File(_root_.util.Directory.GitBucketHome)
    if(!dir.exists){
      dir.mkdirs()
    }
  }
}