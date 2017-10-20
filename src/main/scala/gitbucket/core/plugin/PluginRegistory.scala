package gitbucket.core.plugin

import java.io.{File, FilenameFilter, InputStream}
import java.net.URLClassLoader
import java.nio.file.{Files, Paths, StandardWatchEventKinds}
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import javax.servlet.ServletContext

import gitbucket.core.controller.{Context, ControllerBase}
import gitbucket.core.model.{Account, Issue}
import gitbucket.core.service.ProtectedBranchService.ProtectedBranchReceiveHook
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.DatabaseConfig
import gitbucket.core.util.Directory._
import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.manager.JDBCVersionManager
import io.github.gitbucket.solidbase.model.Module
import org.apache.commons.io.FileUtils
import org.apache.sshd.server.Command
import org.slf4j.LoggerFactory
import play.twirl.api.Html

import scala.collection.JavaConverters._

class PluginRegistry {

  private val plugins = new ConcurrentLinkedQueue[PluginInfo]
  private val javaScripts = new ConcurrentLinkedQueue[(String, String)]
  private val controllers = new ConcurrentLinkedQueue[(ControllerBase, String)]
  private val images = new ConcurrentHashMap[String, String]
  private val renderers = new ConcurrentHashMap[String, Renderer]
  renderers.put("md", MarkdownRenderer)
  renderers.put("markdown", MarkdownRenderer)
  private val repositoryRoutings = new ConcurrentLinkedQueue[GitRepositoryRouting]
  private val accountHooks = new ConcurrentLinkedQueue[AccountHook]
  private val receiveHooks = new ConcurrentLinkedQueue[ReceiveHook]
  receiveHooks.add(new ProtectedBranchReceiveHook())
  private val repositoryHooks = new ConcurrentLinkedQueue[RepositoryHook]
  private val issueHooks = new ConcurrentLinkedQueue[IssueHook]
  private val pullRequestHooks = new ConcurrentLinkedQueue[PullRequestHook]
  private val repositoryHeaders = new ConcurrentLinkedQueue[(RepositoryInfo, Context) => Option[Html]]
  private val globalMenus = new ConcurrentLinkedQueue[(Context) => Option[Link]]
  private val repositoryMenus = new ConcurrentLinkedQueue[(RepositoryInfo, Context) => Option[Link]]
  private val repositorySettingTabs = new ConcurrentLinkedQueue[(RepositoryInfo, Context) => Option[Link]]
  private val profileTabs = new ConcurrentLinkedQueue[(Account, Context) => Option[Link]]
  private val systemSettingMenus = new ConcurrentLinkedQueue[(Context) => Option[Link]]
  private val accountSettingMenus = new ConcurrentLinkedQueue[(Context) => Option[Link]]
  private val dashboardTabs = new ConcurrentLinkedQueue[(Context) => Option[Link]]
  private val issueSidebars = new ConcurrentLinkedQueue[(Issue, RepositoryInfo, Context) => Option[Html]]
  private val assetsMappings = new ConcurrentLinkedQueue[(String, String, ClassLoader)]
  private val textDecorators = new ConcurrentLinkedQueue[TextDecorator]
  private val suggestionProviders = new ConcurrentLinkedQueue[SuggestionProvider]
  suggestionProviders.add(new UserNameSuggestionProvider())
  private val sshCommandProviders = new ConcurrentLinkedQueue[PartialFunction[String, Command]]()

  def addPlugin(pluginInfo: PluginInfo): Unit = plugins.add(pluginInfo)

  def getPlugins(): List[PluginInfo] = plugins.asScala.toList

  def addImage(id: String, bytes: Array[Byte]): Unit = {
    val encoded = Base64.getEncoder.encodeToString(bytes)
    images.put(id, encoded)
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

  def getImage(id: String): String = images.get(id)

  def addController(path: String, controller: ControllerBase): Unit = controllers.add((controller, path))

  @deprecated("Use addController(path: String, controller: ControllerBase) instead", "3.4.0")
  def addController(controller: ControllerBase, path: String): Unit = addController(path, controller)

  def getControllers(): Seq[(ControllerBase, String)] = controllers.asScala.toSeq

  def addJavaScript(path: String, script: String): Unit = javaScripts.add((path, script)) //javaScripts += ((path, script))

  def getJavaScript(currentPath: String): List[String] = javaScripts.asScala.filter(x => currentPath.matches(x._1)).toList.map(_._2)

  def addRenderer(extension: String, renderer: Renderer): Unit = renderers.put(extension, renderer)

  def getRenderer(extension: String): Renderer = renderers.asScala.getOrElse(extension, DefaultRenderer)

  def renderableExtensions: Seq[String] = renderers.keys.asScala.toSeq

  def addRepositoryRouting(routing: GitRepositoryRouting): Unit = repositoryRoutings.add(routing)

  def getRepositoryRoutings(): Seq[GitRepositoryRouting] = repositoryRoutings.asScala.toSeq

  def getRepositoryRouting(repositoryPath: String): Option[GitRepositoryRouting] = {
    PluginRegistry().getRepositoryRoutings().find {
      case GitRepositoryRouting(urlPath, _, _) => {
        repositoryPath.matches("/" + urlPath + "(/.*)?")
      }
    }
  }

  def addAccountHook(accountHook: AccountHook): Unit = accountHooks.add(accountHook)

  def getAccountHooks: Seq[AccountHook] = accountHooks.asScala.toSeq

  def addReceiveHook(commitHook: ReceiveHook): Unit = receiveHooks.add(commitHook)

  def getReceiveHooks: Seq[ReceiveHook] = receiveHooks.asScala.toSeq

  def addRepositoryHook(repositoryHook: RepositoryHook): Unit = repositoryHooks.add(repositoryHook)

  def getRepositoryHooks: Seq[RepositoryHook] = repositoryHooks.asScala.toSeq

  def addIssueHook(issueHook: IssueHook): Unit = issueHooks.add(issueHook)

  def getIssueHooks: Seq[IssueHook] = issueHooks.asScala.toSeq

  def addPullRequestHook(pullRequestHook: PullRequestHook): Unit = pullRequestHooks.add(pullRequestHook)

  def getPullRequestHooks: Seq[PullRequestHook] = pullRequestHooks.asScala.toSeq

  def addRepositoryHeader(repositoryHeader: (RepositoryInfo, Context) => Option[Html]): Unit = repositoryHeaders.add(repositoryHeader)

  def getRepositoryHeaders: Seq[(RepositoryInfo, Context) => Option[Html]] = repositoryHeaders.asScala.toSeq

  def addGlobalMenu(globalMenu: (Context) => Option[Link]): Unit = globalMenus.add(globalMenu)

  def getGlobalMenus: Seq[(Context) => Option[Link]] = globalMenus.asScala.toSeq

  def addRepositoryMenu(repositoryMenu: (RepositoryInfo, Context) => Option[Link]): Unit = repositoryMenus.add(repositoryMenu)

  def getRepositoryMenus: Seq[(RepositoryInfo, Context) => Option[Link]] = repositoryMenus.asScala.toSeq

  def addRepositorySettingTab(repositorySettingTab: (RepositoryInfo, Context) => Option[Link]): Unit = repositorySettingTabs.add(repositorySettingTab)

  def getRepositorySettingTabs: Seq[(RepositoryInfo, Context) => Option[Link]] = repositorySettingTabs.asScala.toSeq

  def addProfileTab(profileTab: (Account, Context) => Option[Link]): Unit = profileTabs.add(profileTab)

  def getProfileTabs: Seq[(Account, Context) => Option[Link]] = profileTabs.asScala.toSeq

  def addSystemSettingMenu(systemSettingMenu: (Context) => Option[Link]): Unit = systemSettingMenus.add(systemSettingMenu)

  def getSystemSettingMenus: Seq[(Context) => Option[Link]] = systemSettingMenus.asScala.toSeq

  def addAccountSettingMenu(accountSettingMenu: (Context) => Option[Link]): Unit = accountSettingMenus.add(accountSettingMenu)

  def getAccountSettingMenus: Seq[(Context) => Option[Link]] = accountSettingMenus.asScala.toSeq

  def addDashboardTab(dashboardTab: (Context) => Option[Link]): Unit = dashboardTabs.add(dashboardTab)

  def getDashboardTabs: Seq[(Context) => Option[Link]] = dashboardTabs.asScala.toSeq

  def addIssueSidebar(issueSidebar: (Issue, RepositoryInfo, Context) => Option[Html]): Unit = issueSidebars.add(issueSidebar)

  def getIssueSidebars: Seq[(Issue, RepositoryInfo, Context) => Option[Html]] = issueSidebars.asScala.toSeq

  def addAssetsMapping(assetsMapping: (String, String, ClassLoader)): Unit = assetsMappings.add(assetsMapping)

  def getAssetsMappings: Seq[(String, String, ClassLoader)] = assetsMappings.asScala.toSeq

  def addTextDecorator(textDecorator: TextDecorator): Unit = textDecorators.add(textDecorator)

  def getTextDecorators: Seq[TextDecorator] = textDecorators.asScala.toSeq

  def addSuggestionProvider(suggestionProvider: SuggestionProvider): Unit = suggestionProviders.add(suggestionProvider)

  def getSuggestionProviders: Seq[SuggestionProvider] = suggestionProviders.asScala.toSeq

  def addSshCommandProvider(sshCommandProvider: PartialFunction[String, Command]): Unit = sshCommandProviders.add(sshCommandProvider)

  def getSshCommandProviders: Seq[PartialFunction[String, Command]] = sshCommandProviders.asScala.toSeq
}

/**
 * Provides entry point to PluginRegistry.
 */
object PluginRegistry {

  private val logger = LoggerFactory.getLogger(classOf[PluginRegistry])

  private var instance = new PluginRegistry()

  private var watcher: PluginWatchThread = null
  private var extraWatcher: PluginWatchThread = null
  private val initializing = new AtomicBoolean(false)

  /**
   * Returns the PluginRegistry singleton instance.
   */
  def apply(): PluginRegistry = instance

  /**
   * Reload all plugins.
   */
  def reload(context: ServletContext, settings: SystemSettings, conn: java.sql.Connection): Unit = synchronized {
    shutdown(context, settings)
    instance = new PluginRegistry()
    initialize(context, settings, conn)
  }

  /**
   * Uninstall a specified plugin.
   */
  def uninstall(pluginId: String, context: ServletContext, settings: SystemSettings, conn: java.sql.Connection): Unit = synchronized {
    instance.getPlugins()
      .collect { case plugin if plugin.pluginId == pluginId => plugin }
      .foreach { plugin =>
//      try {
//        plugin.pluginClass.uninstall(instance, context, settings)
//      } catch {
//        case e: Exception =>
//          logger.error(s"Error during uninstalling plugin: ${plugin.pluginJar.getName}", e)
//      }
        shutdown(context, settings)
        plugin.pluginJar.delete()
        instance = new PluginRegistry()
        initialize(context, settings, conn)
      }
  }

  /**
   * Install a plugin from a specified jar file.
   */
  def install(file: File, context: ServletContext, settings: SystemSettings, conn: java.sql.Connection): Unit = synchronized {
    shutdown(context, settings)
    FileUtils.copyFile(file, new File(PluginHome, file.getName))
    instance = new PluginRegistry()
    initialize(context, settings, conn)
  }

  private def listPluginJars(dir: File): Seq[File] = {
    dir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
    }).toSeq.sortBy(_.getName).reverse
  }

  lazy val extraPluginDir: Option[String] = Option(System.getProperty("gitbucket.pluginDir"))

  /**
   * Initializes all installed plugins.
   */
  def initialize(context: ServletContext, settings: SystemSettings, conn: java.sql.Connection): Unit = synchronized {
    val pluginDir = new File(PluginHome)
    val manager = new JDBCVersionManager(conn)

    // Clean installed directory
    val installedDir = new File(PluginHome, ".installed")
    if(installedDir.exists){
      FileUtils.deleteDirectory(installedDir)
    }
    installedDir.mkdirs()

    val pluginJars = listPluginJars(pluginDir)
    val extraJars = extraPluginDir.map { extraDir => listPluginJars(new File(extraDir)) }.getOrElse(Nil)

    (extraJars ++ pluginJars).foreach { pluginJar =>
      val installedJar = new File(installedDir, pluginJar.getName)

      FileUtils.copyFile(pluginJar, installedJar)

      logger.info(s"Initialize ${pluginJar.getName}")
      val classLoader = new URLClassLoader(Array(installedJar.toURI.toURL), Thread.currentThread.getContextClassLoader)
      try {
        val plugin = classLoader.loadClass("Plugin").getDeclaredConstructor().newInstance().asInstanceOf[Plugin]
        val pluginId = plugin.pluginId

        // Check duplication
        instance.getPlugins().find(_.pluginId == pluginId) match {
          case Some(x) => {
            logger.warn(s"Plugin ${pluginId} is duplicated. ${x.pluginJar.getName} is available.")
          }
          case None => {
            // Migration
            val solidbase = new Solidbase()
            solidbase.migrate(conn, classLoader, DatabaseConfig.liquiDriver, new Module(plugin.pluginId, plugin.versions: _*))

            // Check database version
            val databaseVersion = manager.getCurrentVersion(plugin.pluginId)
            val pluginVersion = plugin.versions.last.getVersion
            if (databaseVersion != pluginVersion) {
              throw new IllegalStateException(s"Plugin version is ${pluginVersion}, but database version is ${databaseVersion}")
            }

            // Initialize
            plugin.initialize(instance, context, settings)
            instance.addPlugin(PluginInfo(
              pluginId      = plugin.pluginId,
              pluginName    = plugin.pluginName,
              pluginVersion = plugin.versions.last.getVersion,
              description   = plugin.description,
              pluginClass   = plugin,
              pluginJar     = pluginJar,
              classLoader   = classLoader
            ))
          }
        }
      } catch {
        case e: Throwable => logger.error(s"Error during plugin initialization: ${pluginJar.getName}", e)
      }
    }

    if(watcher == null){
      watcher = new PluginWatchThread(context, PluginHome)
      watcher.start()
    }

    extraPluginDir.foreach { extraDir =>
      if(extraWatcher == null){
        extraWatcher = new PluginWatchThread(context, extraDir)
        extraWatcher.start()
      }
    }
  }

  def shutdown(context: ServletContext, settings: SystemSettings): Unit = synchronized {
    instance.getPlugins().foreach { plugin =>
      try {
        plugin.pluginClass.shutdown(instance, context, settings)
        if(watcher != null){
          watcher.interrupt()
          watcher = null
        }
        if(extraWatcher != null){
          extraWatcher.interrupt()
          extraWatcher = null
        }
      } catch {
        case e: Exception => {
          logger.error(s"Error during plugin shutdown: ${plugin.pluginJar.getName}", e)
        }
      } finally {
        plugin.classLoader.close()
      }
    }
  }

}

case class Link(
  id: String,
  label: String,
  path: String,
  icon: Option[String] = None
)

class PluginInfoBase(
  val pluginId: String,
  val pluginName: String,
  val pluginVersion: String,
  val description: String
)

case class PluginInfo(
  override val pluginId: String,
  override val pluginName: String,
  override val pluginVersion: String,
  override val description: String,
  pluginClass: Plugin,
  pluginJar: File,
  classLoader: URLClassLoader
) extends PluginInfoBase(pluginId, pluginName, pluginVersion, description)

class PluginWatchThread(context: ServletContext, dir: String) extends Thread with SystemSettingsService {
  import gitbucket.core.model.Profile.profile.blockingApi._
  import scala.collection.JavaConverters._

  private val logger = LoggerFactory.getLogger(classOf[PluginWatchThread])

  override def run(): Unit = {
    val path = Paths.get(dir)
    if(!Files.exists(path)){
      Files.createDirectories(path)
    }
    val fs = path.getFileSystem
    val watcher = fs.newWatchService

    val watchKey = path.register(watcher,
      StandardWatchEventKinds.ENTRY_CREATE,
      StandardWatchEventKinds.ENTRY_MODIFY,
      StandardWatchEventKinds.ENTRY_DELETE,
      StandardWatchEventKinds.OVERFLOW)

    logger.info("Start PluginWatchThread: " + path)

    try {
      while (watchKey.isValid()) {
        val detectedWatchKey = watcher.take()
        val events = detectedWatchKey.pollEvents.asScala.filter { e =>
          e.context.toString != ".installed" && !e.context.toString.endsWith(".bak")
        }
        if(events.nonEmpty){
          events.foreach { event =>
            logger.info(event.kind + ": " + event.context)
          }
          new Thread {
            override def run(): Unit = {
                gitbucket.core.servlet.Database() withTransaction { session =>
                logger.info("Reloading plugins...")
                PluginRegistry.reload(context, loadSystemSettings(), session.conn)
                logger.info("Reloading finished.")
              }
            }
          }.start()
        }
        detectedWatchKey.reset()
      }
    } catch {
      case _: InterruptedException => watchKey.cancel()
    }

    logger.info("Shutdown PluginWatchThread")
  }

}
