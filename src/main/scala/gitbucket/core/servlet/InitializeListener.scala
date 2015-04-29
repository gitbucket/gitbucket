package gitbucket.core.servlet

import java.sql.{DriverManager, Connection}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.util._
import org.apache.commons.io.FileUtils
import javax.servlet.{ServletContextListener, ServletContextEvent}
import org.slf4j.LoggerFactory
import ControlUtil._
import gitbucket.core.util.Versions

/**
 * Initialize GitBucket system.
 * Update database schema and load plug-ins automatically in the context initializing.
 */
class InitializeListener extends ServletContextListener with SystemSettingsService {
  import AutoUpdate._

  private val logger = LoggerFactory.getLogger(classOf[InitializeListener])

  override def contextInitialized(event: ServletContextEvent): Unit = {
    val dataDir = event.getServletContext.getInitParameter("gitbucket.home")
    if(dataDir != null){
      System.setProperty("gitbucket.home", dataDir)
    }
    org.h2.Driver.load()

    using(getConnection()){ conn =>
      // Migration
      logger.debug("Start schema update")
      Versions.update(conn, headVersion, getCurrentVersion(), versions, Thread.currentThread.getContextClassLoader){ conn =>
        FileUtils.writeStringToFile(versionFile, headVersion.versionString, "UTF-8")
      }
      // Load plugins
      logger.debug("Initialize plugins")
      PluginRegistry.initialize(event.getServletContext, loadSystemSettings(), conn)
    }

  }

  override def contextDestroyed(event: ServletContextEvent): Unit = {
    // Shutdown plugins
    PluginRegistry.shutdown(event.getServletContext, loadSystemSettings())
    // Close datasource
    Database.closeDataSource()
  }

  private def getConnection(): Connection =
    DriverManager.getConnection(
      DatabaseConfig.url,
      DatabaseConfig.user,
      DatabaseConfig.password)
}
