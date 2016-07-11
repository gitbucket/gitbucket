package gitbucket.core.plugin

import javax.servlet.ServletContext
import gitbucket.core.controller.{Context, ControllerBase}
import gitbucket.core.model.Account
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.util.ControlUtil._
import io.github.gitbucket.solidbase.model.Version

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
  def images(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, Array[Byte])] = Nil

  /**
   * Override to declare this plug-in provides controllers.
   */
  val controllers: Seq[(String, ControllerBase)] = Nil

  /**
   * Override to declare this plug-in provides controllers.
   */
  def controllers(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, ControllerBase)] = Nil

  /**
   * Override to declare this plug-in provides JavaScript.
   */
  val javaScripts: Seq[(String, String)] = Nil

  /**
   * Override to declare this plug-in provides JavaScript.
   */
  def javaScripts(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, String)] = Nil

  /**
   * Override to declare this plug-in provides renderers.
   */
  val renderers: Seq[(String, Renderer)] = Nil

  /**
   * Override to declare this plug-in provides renderers.
   */
  def renderers(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, Renderer)] = Nil

  /**
   * Override to add git repository routings.
   */
  val repositoryRoutings: Seq[GitRepositoryRouting] = Nil

  /**
   * Override to add git repository routings.
   */
  def repositoryRoutings(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[GitRepositoryRouting] = Nil

  /**
   * Override to add receive hooks.
   */
  val receiveHooks: Seq[ReceiveHook] = Nil

  /**
   * Override to add receive hooks.
   */
  def receiveHooks(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[ReceiveHook] = Nil

  /**
   * Override to add global menus.
   */
  val globalMenus: Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add global menus.
   */
  def globalMenus(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add repository menus.
   */
  val repositoryMenus: Seq[(RepositoryInfo, Context) => Option[Link]] = Nil

  /**
   * Override to add repository menus.
   */
  def repositoryMenus(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(RepositoryInfo, Context) => Option[Link]] = Nil

  /**
   * Override to add repository setting tabs.
   */
  val repositorySettingTabs: Seq[(RepositoryInfo, Context) => Option[Link]] = Nil

  /**
   * Override to add repository setting tabs.
   */
  def repositorySettingTabs(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(RepositoryInfo, Context) => Option[Link]] = Nil

  /**
   * Override to add profile tabs.
   */
  val profileTabs: Seq[(Account, Context) => Option[Link]] = Nil

  /**
   * Override to add profile tabs.
   */
  def profileTabs(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(Account, Context) => Option[Link]] = Nil

  /**
   * Override to add system setting menus.
   */
  val systemSettingMenus: Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add system setting menus.
   */
  def systemSettingMenus(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add account setting menus.
   */
  val accountSettingMenus: Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add account setting menus.
   */
  def accountSettingMenus(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add dashboard tabs.
   */
  val dashboardTabs: Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add dashboard tabs.
   */
  def dashboardTabs(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(Context) => Option[Link]] = Nil

  /**
   * Override to add assets mappings.
   */
  val assetsMappings: Seq[(String, String)] = Nil

  /**
   * Override to add assets mappings.
   */
  def assetsMappings(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, String)] = Nil

  /**
   * This method is invoked in initialization of plugin system.
   * Register plugin functionality to PluginRegistry.
   */
  def initialize(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Unit = {
    (images ++ images(registry, context, settings)).foreach { case (id, in) =>
      registry.addImage(id, in)
    }
    (controllers ++ controllers(registry, context, settings)).foreach { case (path, controller) =>
      registry.addController(path, controller)
    }
    (javaScripts ++ javaScripts(registry, context, settings)).foreach { case (path, script) =>
      registry.addJavaScript(path, script)
    }
    (renderers ++ renderers(registry, context, settings)).foreach { case (extension, renderer) =>
      registry.addRenderer(extension, renderer)
    }
    (repositoryRoutings ++ repositoryRoutings(registry, context, settings)).foreach { routing =>
      registry.addRepositoryRouting(routing)
    }
    (receiveHooks ++ receiveHooks(registry, context, settings)).foreach { receiveHook =>
      registry.addReceiveHook(receiveHook)
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
    (assetsMappings ++ assetsMappings(registry, context, settings)).foreach { assetMapping =>
      registry.addAssetsMapping((assetMapping._1, assetMapping._2, getClass.getClassLoader))
    }
  }

  /**
   * This method is invoked in shutdown of plugin system.
   * If the plugin has any resources, release them in this method.
   */
  def shutdown(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Unit = {}

  /**
   * Helper method to get a resource from classpath.
   */
  protected def fromClassPath(path: String): Array[Byte] =
    using(getClass.getClassLoader.getResourceAsStream(path)){ in =>
      val bytes = new Array[Byte](in.available)
      in.read(bytes)
      bytes
    }

}
