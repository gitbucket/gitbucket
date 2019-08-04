package gitbucket.core.plugin

import javax.servlet.ServletContext

import gitbucket.core.controller.{Context, ControllerBase}
import gitbucket.core.model.{Account, Issue}
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.SystemSettingsService.SystemSettings
import io.github.gitbucket.solidbase.model.Version
import org.apache.sshd.server.command.Command
import play.twirl.api.Html
import scala.util.Using

/**
 * Trait for define plugin interface.
 * To provide a plugin, put a Plugin class which extends this class into the package root.
 */
abstract class Plugin {

  val pluginId: String
  val pluginName: String
  val description: String
  val versions: Seq[Version]

  /**
   * Override to declare this plug-in provides images.
   */
  val images: Seq[(String, Array[Byte])] = Nil

  /**
   * Override to declare this plug-in provides images.
   */
  def images(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, Array[Byte])] =
    Nil

  /**
   * Override to declare this plug-in provides controllers.
   */
  val controllers: Seq[(String, ControllerBase)] = Nil

  /**
   * Override to declare this plug-in provides controllers.
   */
  def controllers(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(String, ControllerBase)] = Nil

  /**
   * Override to declare this plug-in provides anonymous accessible paths.
   */
  val anonymousAccessiblePaths: Seq[String] = Nil

  /**
   * Override to declare this plug-in provides anonymous accessible paths.
   */
  def anonymousAccessiblePaths(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[String] = Nil

  /**
   * Override to declare this plug-in provides JavaScript.
   */
  val javaScripts: Seq[(String, String)] = Nil

  /**
   * Override to declare this plug-in provides JavaScript.
   */
  def javaScripts(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, String)] =
    Nil

  /**
   * Override to declare this plug-in provides renderers.
   */
  val renderers: Seq[(String, Renderer)] = Nil

  /**
   * Override to declare this plug-in provides renderers.
   */
  def renderers(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, Renderer)] =
    Nil

  /**
   * Override to add git repository routings.
   */
  val repositoryRoutings: Seq[GitRepositoryRouting] = Nil

  /**
   * Override to add git repository routings.
   */
  def repositoryRoutings(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[GitRepositoryRouting] = Nil

  /**
   * Override to add account hooks.
   */
  val accountHooks: Seq[AccountHook] = Nil

  /**
   * Override to add account hooks.
   */
  def accountHooks(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[AccountHook] = Nil

  /**
   * Override to add receive hooks.
   */
  val receiveHooks: Seq[ReceiveHook] = Nil

  /**
   * Override to add receive hooks.
   */
  def receiveHooks(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[ReceiveHook] = Nil

  /**
   * Override to add repository hooks.
   */
  val repositoryHooks: Seq[RepositoryHook] = Nil

  /**
   * Override to add repository hooks.
   */
  def repositoryHooks(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[RepositoryHook] = Nil

  /**
   * Override to add issue hooks.
   */
  val issueHooks: Seq[IssueHook] = Nil

  /**
   * Override to add issue hooks.
   */
  def issueHooks(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[IssueHook] = Nil

  /**
   * Override to add pull request hooks.
   */
  val pullRequestHooks: Seq[PullRequestHook] = Nil

  /**
   * Override to add pull request hooks.
   */
  def pullRequestHooks(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[PullRequestHook] = Nil

  /**
   * Override to add repository headers.
   */
  val repositoryHeaders: Seq[(RepositoryInfo, Context) => Option[Html]] = Nil

  /**
   * Override to add repository headers.
   */
  def repositoryHeaders(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(RepositoryInfo, Context) => Option[Html]] = Nil

  /**
   * Override to add global menus.
   */
  val globalMenus: Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add global menus.
   */
  def globalMenus(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add repository menus.
   */
  val repositoryMenus: Seq[(RepositoryInfo, Context) => Option[Link]] = Nil

  /**
   * Override to add repository menus.
   */
  def repositoryMenus(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(RepositoryInfo, Context) => Option[Link]] = Nil

  /**
   * Override to add repository setting tabs.
   */
  val repositorySettingTabs: Seq[(RepositoryInfo, Context) => Option[Link]] = Nil

  /**
   * Override to add repository setting tabs.
   */
  def repositorySettingTabs(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(RepositoryInfo, Context) => Option[Link]] = Nil

  /**
   * Override to add profile tabs.
   */
  val profileTabs: Seq[(Account, Context) => Option[Link]] = Nil

  /**
   * Override to add profile tabs.
   */
  def profileTabs(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(Account, Context) => Option[Link]] = Nil

  /**
   * Override to add system setting menus.
   */
  val systemSettingMenus: Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add system setting menus.
   */
  def systemSettingMenus(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add account setting menus.
   */
  val accountSettingMenus: Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add account setting menus.
   */
  def accountSettingMenus(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add dashboard tabs.
   */
  val dashboardTabs: Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add dashboard tabs.
   */
  def dashboardTabs(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add issue sidebars.
   */
  val issueSidebars: Seq[(Issue, RepositoryInfo, Context) => Option[Html]] = Nil

  /**
   * Override to add issue sidebars.
   */
  def issueSidebars(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(Issue, RepositoryInfo, Context) => Option[Html]] = Nil

  /**
   * Override to add assets mappings.
   */
  val assetsMappings: Seq[(String, String)] = Nil

  /**
   * Override to add assets mappings.
   */
  def assetsMappings(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(String, String)] = Nil

  /**
   * Override to add text decorators.
   */
  val textDecorators: Seq[TextDecorator] = Nil

  /**
   * Override to add text decorators.
   */
  def textDecorators(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[TextDecorator] =
    Nil

  /**
   * Override to add suggestion provider.
   */
  val suggestionProviders: Seq[SuggestionProvider] = Nil

  /**
   * Override to add suggestion provider.
   */
  def suggestionProviders(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[SuggestionProvider] = Nil

  /**
   * Override to add ssh command providers.
   */
  val sshCommandProviders: Seq[PartialFunction[String, Command]] = Nil

  /**
   * Override to add ssh command providers.
   */
  def sshCommandProviders(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[PartialFunction[String, Command]] = Nil

  /**
   * This method is invoked in initialization of plugin system.
   * Register plugin functionality to PluginRegistry.
   */
  def initialize(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Unit = {
    (images ++ images(registry, context, settings)).foreach {
      case (id, in) =>
        registry.addImage(id, in)
    }
    (controllers ++ controllers(registry, context, settings)).foreach {
      case (path, controller) =>
        registry.addController(path, controller)
    }
    (anonymousAccessiblePaths ++ anonymousAccessiblePaths(registry, context, settings)).foreach {
      case (path) =>
        registry.addAnonymousAccessiblePath(path)
    }
    (javaScripts ++ javaScripts(registry, context, settings)).foreach {
      case (path, script) =>
        registry.addJavaScript(path, script)
    }
    (renderers ++ renderers(registry, context, settings)).foreach {
      case (extension, renderer) =>
        registry.addRenderer(extension, renderer)
    }
    (repositoryRoutings ++ repositoryRoutings(registry, context, settings)).foreach { routing =>
      registry.addRepositoryRouting(routing)
    }
    (accountHooks ++ accountHooks(registry, context, settings)).foreach { accountHook =>
      registry.addAccountHook(accountHook)
    }
    (receiveHooks ++ receiveHooks(registry, context, settings)).foreach { receiveHook =>
      registry.addReceiveHook(receiveHook)
    }
    (repositoryHooks ++ repositoryHooks(registry, context, settings)).foreach { repositoryHook =>
      registry.addRepositoryHook(repositoryHook)
    }
    (issueHooks ++ issueHooks(registry, context, settings)).foreach { issueHook =>
      registry.addIssueHook(issueHook)
    }
    (pullRequestHooks ++ pullRequestHooks(registry, context, settings)).foreach { pullRequestHook =>
      registry.addPullRequestHook(pullRequestHook)
    }
    (repositoryHeaders ++ repositoryHeaders(registry, context, settings)).foreach { repositoryHeader =>
      registry.addRepositoryHeader(repositoryHeader)
    }
    (globalMenus ++ globalMenus(registry, context, settings)).foreach { globalMenu =>
      registry.addGlobalMenu(globalMenu)
    }
    (repositoryMenus ++ repositoryMenus(registry, context, settings)).foreach { repositoryMenu =>
      registry.addRepositoryMenu(repositoryMenu)
    }
    (repositorySettingTabs ++ repositorySettingTabs(registry, context, settings)).foreach { repositorySettingTab =>
      registry.addRepositorySettingTab(repositorySettingTab)
    }
    (profileTabs ++ profileTabs(registry, context, settings)).foreach { profileTab =>
      registry.addProfileTab(profileTab)
    }
    (systemSettingMenus ++ systemSettingMenus(registry, context, settings)).foreach { systemSettingMenu =>
      registry.addSystemSettingMenu(systemSettingMenu)
    }
    (accountSettingMenus ++ accountSettingMenus(registry, context, settings)).foreach { accountSettingMenu =>
      registry.addAccountSettingMenu(accountSettingMenu)
    }
    (dashboardTabs ++ dashboardTabs(registry, context, settings)).foreach { dashboardTab =>
      registry.addDashboardTab(dashboardTab)
    }
    (issueSidebars ++ issueSidebars(registry, context, settings)).foreach { issueSidebarComponent =>
      registry.addIssueSidebar(issueSidebarComponent)
    }
    (assetsMappings ++ assetsMappings(registry, context, settings)).foreach { assetMapping =>
      registry.addAssetsMapping((assetMapping._1, assetMapping._2, getClass.getClassLoader))
    }
    (textDecorators ++ textDecorators(registry, context, settings)).foreach { textDecorator =>
      registry.addTextDecorator(textDecorator)
    }
    (suggestionProviders ++ suggestionProviders(registry, context, settings)).foreach { suggestionProvider =>
      registry.addSuggestionProvider(suggestionProvider)
    }
    (sshCommandProviders ++ sshCommandProviders(registry, context, settings)).foreach { sshCommandProvider =>
      registry.addSshCommandProvider(sshCommandProvider)
    }
  }

  /**
   * This method is invoked when the plugin system is shutting down.
   * If the plugin has any resources, release them in this method.
   */
  def shutdown(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Unit = {}

//  /**
//   * This method is invoked when this plugin is uninstalled.
//   * Cleanup database or any other resources in this method if necessary.
//   */
//  def uninstall(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Unit = {}

  /**
   * Helper method to get a resource from classpath.
   */
  protected def fromClassPath(path: String): Array[Byte] =
    Using.resource(getClass.getClassLoader.getResourceAsStream(path)) { in =>
      val bytes = new Array[Byte](in.available)
      in.read(bytes)
      bytes
    }

}
