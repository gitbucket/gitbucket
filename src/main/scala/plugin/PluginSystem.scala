package plugin

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import util.Directory._
import util.ControlUtil._
import org.apache.commons.io.{IOUtils, FileUtils}
import Security._
import service.PluginService
import model.Profile._
import profile.simple._
import java.io.FileInputStream
import java.sql.Connection
import app.Context
import service.RepositoryService.RepositoryInfo

/**
 * Provides extension points to plug-ins.
 */
object PluginSystem extends PluginService {

  private val logger = LoggerFactory.getLogger(PluginSystem.getClass)

  private val initialized = new AtomicBoolean(false)
  private val pluginsMap = scala.collection.mutable.Map[String, Plugin]()
  private val repositoriesList = scala.collection.mutable.ListBuffer[PluginRepository]()

  def install(plugin: Plugin): Unit = {
    pluginsMap.put(plugin.id, plugin)
  }

  def plugins: List[Plugin] = pluginsMap.values.toList

  def uninstall(id: String)(implicit session: Session): Unit = {
    pluginsMap.remove(id)

    // Delete from PLUGIN table
    deletePlugin(id)

    // Drop tables
    val pluginDir = new java.io.File(PluginHome)
    val sqlFile = new java.io.File(pluginDir, s"${id}/sql/drop.sql")
    if(sqlFile.exists){
      val sql = IOUtils.toString(new FileInputStream(sqlFile), "UTF-8")
      using(session.conn.createStatement()){ stmt =>
        stmt.executeUpdate(sql)
      }
    }
  }

  def repositories: List[PluginRepository] = repositoriesList.toList

  /**
   * Initializes the plugin system. Load scripts from GITBUCKET_HOME/plugins.
   */
  def init()(implicit session: Session): Unit = {
    if(initialized.compareAndSet(false, true)){
      // Load installed plugins
      val pluginDir = new java.io.File(PluginHome)
      if(pluginDir.exists && pluginDir.isDirectory){
        pluginDir.listFiles.filter(f => f.isDirectory && !f.getName.startsWith(".")).foreach { dir =>
          installPlugin(dir.getName)
        }
      }
      // Add default plugin repositories
      repositoriesList += PluginRepository("central", "https://github.com/takezoe/gitbucket_plugins.git")
    }
  }

  // TODO Method name seems to not so good.
  def installPlugin(id: String)(implicit session: Session): Unit = {
    val pluginHome = new java.io.File(PluginHome)
    val pluginDir  = new java.io.File(pluginHome, id)

    val scalaFile = new java.io.File(pluginDir, "plugin.scala")
    if(scalaFile.exists && scalaFile.isFile){
      val properties = new java.util.Properties()
      using(new java.io.FileInputStream(new java.io.File(pluginDir, "plugin.properties"))){ in =>
        properties.load(in)
      }

      val pluginId     = properties.getProperty("id")
      val version      = properties.getProperty("version")
      val author       = properties.getProperty("author")
      val url          = properties.getProperty("url")
      val description  = properties.getProperty("description")

      val source = s"""
        |val id          = "${pluginId}"
        |val version     = "${version}"
        |val author      = "${author}"
        |val url         = "${url}"
        |val description = "${description}"
      """.stripMargin + FileUtils.readFileToString(scalaFile, "UTF-8")

      try {
        // Compile and eval Scala source code
        ScalaPlugin.eval(pluginDir.listFiles.filter(_.getName.endsWith(".scala.html")).map { file =>
          ScalaPlugin.compileTemplate(
            id.replaceAll("-", ""),
            file.getName.replaceAll("\\.scala\\.html$", ""),
            IOUtils.toString(new FileInputStream(file)))
        }.mkString("\n") + source)

        // Migrate database
        val plugin = getPlugin(pluginId)
        if(plugin.isEmpty){
          registerPlugin(model.Plugin(pluginId, version))
          migrate(session.conn, pluginId, "0.0")
        } else {
          updatePlugin(model.Plugin(pluginId, version))
          migrate(session.conn, pluginId, plugin.get.version)
        }
      } catch {
        case e: Throwable => logger.warn(s"Error in plugin loading for ${scalaFile.getAbsolutePath}", e)
      }
    }
  }

  // TODO Should PluginSystem provide a way to migrate resources other than H2?
  private def migrate(conn: Connection, pluginId: String, current: String): Unit = {
    val pluginDir = new java.io.File(PluginHome)

    // TODO Is ot possible to use this migration system in GitBucket migration?
    val dim = current.split("\\.")
    val currentVersion = Version(dim(0).toInt, dim(1).toInt)

    val sqlDir = new java.io.File(pluginDir, s"${pluginId}/sql")
    if(sqlDir.exists && sqlDir.isDirectory){
      sqlDir.listFiles.filter(_.getName.endsWith(".sql")).map { file =>
        val array = file.getName.replaceFirst("\\.sql", "").split("_")
        Version(array(0).toInt, array(1).toInt)
      }
      .sorted.reverse.takeWhile(_ > currentVersion)
      .reverse.foreach { version =>
        val sqlFile = new java.io.File(pluginDir, s"${pluginId}/sql/${version.major}_${version.minor}.sql")
        val sql = IOUtils.toString(new FileInputStream(sqlFile), "UTF-8")
        using(conn.createStatement()){ stmt =>
          stmt.executeUpdate(sql)
        }
      }
    }
  }

  case class Version(major: Int, minor: Int) extends Ordered[Version] {

    override def compare(that: Version): Int = {
      if(major != that.major){
        major.compare(that.major)
      } else{
        minor.compare(that.minor)
      }
    }

    def displayString: String = major + "." + minor
  }

  def repositoryMenus       : List[RepositoryMenu]   = pluginsMap.values.flatMap(_.repositoryMenus).toList
  def globalMenus           : List[GlobalMenu]       = pluginsMap.values.flatMap(_.globalMenus).toList
  def repositoryActions     : List[RepositoryAction] = pluginsMap.values.flatMap(_.repositoryActions).toList
  def globalActions         : List[Action]           = pluginsMap.values.flatMap(_.globalActions).toList
  def javaScripts           : List[JavaScript]       = pluginsMap.values.flatMap(_.javaScripts).toList

  // Case classes to hold plug-ins information internally in GitBucket
  case class PluginRepository(id: String, url: String)
  case class GlobalMenu(label: String, url: String, icon: String, condition: Context => Boolean)
  case class RepositoryMenu(label: String, name: String, url: String, icon: String, condition: Context => Boolean)
  case class Action(method: String, path: String, security: Security, function: (HttpServletRequest, HttpServletResponse, Context) => Any)
  case class RepositoryAction(method: String, path: String, security: Security, function: (HttpServletRequest, HttpServletResponse, Context, RepositoryInfo) => Any)
  case class Button(label: String, href: String)
  case class JavaScript(filter: String => Boolean, script: String)

  /**
   * Checks whether the plugin is updatable.
   */
  def isUpdatable(oldVersion: String, newVersion: String): Boolean = {
    if(oldVersion == newVersion){
      false
    } else {
      val dim1 = oldVersion.split("\\.").map(_.toInt)
      val dim2 = newVersion.split("\\.").map(_.toInt)
      dim1.zip(dim2).foreach { case (a, b) =>
        if(a < b){
          return true
        } else if(a > b){
          return false
        }
      }
      return false
    }
  }

}
