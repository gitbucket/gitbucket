
import java.util.EnumSet
import javax.servlet._

import gitbucket.core.controller._
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.servlet._
import gitbucket.core.util.Directory
import org.scalatra._


class ScalatraBootstrap extends LifeCycle with SystemSettingsService {
  override def init(context: ServletContext) {

    val settings = loadSystemSettings()
    if(settings.baseUrl.exists(_.startsWith("https://"))) {
      context.getSessionCookieConfig.setSecure(true)
    }

    // Register TransactionFilter and BasicAuthenticationFilter at first
    context.addFilter("transactionFilter", new TransactionFilter)
    context.getFilterRegistration("transactionFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/*")
    context.addFilter("gitAuthenticationFilter", new GitAuthenticationFilter)
    context.getFilterRegistration("gitAuthenticationFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/git/*")
    context.addFilter("apiAuthenticationFilter", new ApiAuthenticationFilter)
    context.getFilterRegistration("apiAuthenticationFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/api/v3/*")
    context.addFilter("ghCompatRepositoryAccessFilter", new GHCompatRepositoryAccessFilter)
    context.getFilterRegistration("ghCompatRepositoryAccessFilter").addMappingForUrlPatterns(EnumSet.allOf(classOf[DispatcherType]), true, "/*")

    // Register controllers
    context.mount(new AnonymousAccessController, "/*")

    PluginRegistry().getControllers.foreach { case (controller, path) =>
      context.mount(controller, path)
    }

    context.mount(new IndexController, "/")
    context.mount(new ApiController, "/api/v3")
    context.mount(new FileUploadController, "/upload")
    context.mount(new SystemSettingsController, "/admin")
    context.mount(new DashboardController, "/*")
    context.mount(new AccountController, "/*")
    context.mount(new RepositoryViewerController, "/*")
    context.mount(new WikiController, "/*")
    context.mount(new LabelsController, "/*")
    context.mount(new PrioritiesController, "/*")
    context.mount(new MilestonesController, "/*")
    context.mount(new IssuesController, "/*")
    context.mount(new PullRequestsController, "/*")
    context.mount(new RepositorySettingsController, "/*")

    // Create GITBUCKET_HOME directory if it does not exist
    val dir = new java.io.File(Directory.GitBucketHome)
    if(!dir.exists){
      dir.mkdirs()
    }
  }

  override def destroy(context: ServletContext): Unit = {
    Database.closeDataSource()
  }
}
