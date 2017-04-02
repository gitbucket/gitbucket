package gitbucket.core.plugin

import java.io.{File, FilenameFilter, InputStream}
import java.net.URLClassLoader
import java.util.Base64
import javax.servlet.ServletContext

import gitbucket.core.controller.{Context, ControllerBase}
import gitbucket.core.model.Account
import gitbucket.core.service.ProtectedBranchService.ProtectedBranchReceiveHook
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.DatabaseConfig
import gitbucket.core.util.Directory._
import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.manager.JDBCVersionManager
import io.github.gitbucket.solidbase.model.Module
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class PluginRegistry {

  private val plugins = new ListBuffer[PluginInfo]
  private val javaScripts = new ListBuffer[(String, String)]
  private val controllers = new ListBuffer[(ControllerBase, String)]
  private val images = mutable.Map[String, String]()
  private val renderers = mutable.Map[String, Renderer]()
  renderers ++= Seq(
    "md" -> MarkdownRenderer, "markdown" -> MarkdownRenderer
  )
  private val repositoryRoutings = new ListBuffer[GitRepositoryRouting]
  private val receiveHooks = new ListBuffer[ReceiveHook]
  receiveHooks += new ProtectedBranchReceiveHook()

  private val repositoryHooks = new ListBuffer[RepositoryHook]
  private val globalMenus = new ListBuffer[(Context) => Option[Link]]
  private val repositoryMenus = new ListBuffer[(RepositoryInfo, Context) => Option[Link]]
  private val repositorySettingTabs = new ListBuffer[(RepositoryInfo, Context) => Option[Link]]
  private val profileTabs = new ListBuffer[(Account, Context) => Option[Link]]
  private val systemSettingMenus = new ListBuffer[(Context) => Option[Link]]
  private val accountSettingMenus = new ListBuffer[(Context) => Option[Link]]
  private val dashboardTabs = new ListBuffer[(Context) => Option[Link]]
  private val assetsMappings = new ListBuffer[(String, String, ClassLoader)]
  private val textDecorators = new ListBuffer[TextDecorator]

  private val suggestionProviders = new ListBuffer[SuggestionProvider]
  suggestionProviders += new UserNameSuggestionProvider()

  def addPlugin(pluginInfo: PluginInfo): Unit = plugins += pluginInfo

  def getPlugins(): List[PluginInfo] = plugins.toList

  def addImage(id: String, bytes: Array[Byte]): Unit = {
    val encoded = Base64.getEncoder.encodeToString(bytes)
    images += ((id, encoded))
  }

  @deprecated("Use addImage(id: String, bytes: Array[Byte]) instead", "3.4.0")
  def addImage(id: String, in: InputStream): Unit = {
    val bytes = using(in){ in =>
      val bytes = new Array[Byte](in.available)
      in.read(bytes)
      bytes
    }
    addImage(id, bytes)
  }

  def getImage(id: String): String = images(id)

  def addController(path: String, controller: ControllerBase): Unit = controllers += ((controller, path))

  @deprecated("Use addController(path: String, controller: ControllerBase) instead", "3.4.0")
  def addController(controller: ControllerBase, path: String): Unit = addController(path, controller)

  def getControllers(): Seq[(ControllerBase, String)] = controllers.toSeq

  def addJavaScript(path: String, script: String): Unit = javaScripts += ((path, script))

  def getJavaScript(currentPath: String): List[String] = javaScripts.filter(x => currentPath.matches(x._1)).toList.map(_._2)

  def addRenderer(extension: String, renderer: Renderer): Unit = renderers += ((extension, renderer))

  def getRenderer(extension: String): Renderer = renderers.get(extension).getOrElse(DefaultRenderer)

  def renderableExtensions: Seq[String] = renderers.keys.toSeq

  def addRepositoryRouting(routing: GitRepositoryRouting): Unit = repositoryRoutings += routing

  def getRepositoryRoutings(): Seq[GitRepositoryRouting] = repositoryRoutings.toSeq

  def getRepositoryRouting(repositoryPath: String): Option[GitRepositoryRouting] = {
    PluginRegistry().getRepositoryRoutings().find {
      case GitRepositoryRouting(urlPath, _, _) => {
        repositoryPath.matches("/" + urlPath + "(/.*)?")
      }
    }
  }

  def addReceiveHook(commitHook: ReceiveHook): Unit = receiveHooks += commitHook

  def getReceiveHooks: Seq[ReceiveHook] = receiveHooks.toSeq

  def addRepositoryHook(repositoryHook: RepositoryHook): Unit = repositoryHooks += repositoryHook

  def getRepositoryHooks: Seq[RepositoryHook] = repositoryHooks.toSeq

  def addGlobalMenu(globalMenu: (Context) => Option[Link]): Unit = globalMenus += globalMenu

  def getGlobalMenus: Seq[(Context) => Option[Link]] = globalMenus.toSeq

  def addRepositoryMenu(repositoryMenu: (RepositoryInfo, Context) => Option[Link]): Unit = repositoryMenus += repositoryMenu

  def getRepositoryMenus: Seq[(RepositoryInfo, Context) => Option[Link]] = repositoryMenus.toSeq

  def addRepositorySettingTab(repositorySettingTab: (RepositoryInfo, Context) => Option[Link]): Unit = repositorySettingTabs += repositorySettingTab

  def getRepositorySettingTabs: Seq[(RepositoryInfo, Context) => Option[Link]] = repositorySettingTabs.toSeq

  def addProfileTab(profileTab: (Account, Context) => Option[Link]): Unit = profileTabs += profileTab

  def getProfileTabs: Seq[(Account, Context) => Option[Link]] = profileTabs.toSeq

  def addSystemSettingMenu(systemSettingMenu: (Context) => Option[Link]): Unit = systemSettingMenus += systemSettingMenu

  def getSystemSettingMenus: Seq[(Context) => Option[Link]] = systemSettingMenus.toSeq

  def addAccountSettingMenu(accountSettingMenu: (Context) => Option[Link]): Unit = accountSettingMenus += accountSettingMenu

  def getAccountSettingMenus: Seq[(Context) => Option[Link]] = accountSettingMenus.toSeq

  def addDashboardTab(dashboardTab: (Context) => Option[Link]): Unit = dashboardTabs += dashboardTab

  def getDashboardTabs: Seq[(Context) => Option[Link]] = dashboardTabs.toSeq

  def addAssetsMapping(assetsMapping: (String, String, ClassLoader)): Unit = assetsMappings += assetsMapping

  def getAssetsMappings: Seq[(String, String, ClassLoader)] = assetsMappings.toSeq

  def addTextDecorator(textDecorator: TextDecorator): Unit = textDecorators += textDecorator

  def getTextDecorators: Seq[TextDecorator] = textDecorators.toSeq

  def addSuggestionProvider(suggestionProvider: SuggestionProvider): Unit = suggestionProviders += suggestionProvider

  def getSuggestionProviders: Seq[SuggestionProvider] = suggestionProviders.toSeq
}

/**
 * Provides entry point to PluginRegistry.
 */
object PluginRegistry {

  private val logger = LoggerFactory.getLogger(classOf[PluginRegistry])

  private val instance = new PluginRegistry()

  /**
   * Returns the PluginRegistry singleton instance.
   */
  def apply(): PluginRegistry = instance

  /**
   * Initializes all installed plugins.
   */
  def initialize(context: ServletContext, settings: SystemSettings, conn: java.sql.Connection): Unit = {
    val pluginDir = new File(PluginHome)
    val manager = new JDBCVersionManager(conn)

    if(pluginDir.exists && pluginDir.isDirectory){
      pluginDir.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
      }).foreach { pluginJar =>
        val classLoader = new URLClassLoader(Array(pluginJar.toURI.toURL), Thread.currentThread.getContextClassLoader)
        try {
          val plugin = classLoader.loadClass("Plugin").getDeclaredConstructor().newInstance().asInstanceOf[Plugin]

          // Migration
          val solidbase = new Solidbase()
          solidbase.migrate(conn, classLoader, DatabaseConfig.liquiDriver, new Module(plugin.pluginId, plugin.versions: _*))

          // Check version
          val databaseVersion = manager.getCurrentVersion(plugin.pluginId)
          val pluginVersion = plugin.versions.last.getVersion
          if(databaseVersion != pluginVersion){
            throw new IllegalStateException(s"Plugin version is ${pluginVersion}, but database version is ${databaseVersion}")
          }

          // Initialize
          plugin.initialize(instance, context, settings)
          instance.addPlugin(PluginInfo(
            pluginId      = plugin.pluginId,
            pluginName    = plugin.pluginName,
            pluginVersion = plugin.versions.last.getVersion,
            description   = plugin.description,
            pluginClass   = plugin
          ))

        } catch {
          case e: Throwable => {
            logger.error(s"Error during plugin initialization: ${pluginJar.getAbsolutePath}", e)
          }
        }
      }
    }
  }

  def shutdown(context: ServletContext, settings: SystemSettings): Unit = {
    instance.getPlugins().foreach { pluginInfo =>
      try {
        pluginInfo.pluginClass.shutdown(instance, context, settings)
      } catch {
        case e: Exception => {
          logger.error(s"Error during plugin shutdown", e)
        }
      }
    }
  }


}

case class Link(id: String, label: String, path: String, icon: Option[String] = None)

case class PluginInfo(
  pluginId: String,
  pluginName: String,
  pluginVersion: String,
  description: String,
  pluginClass: Plugin
)
