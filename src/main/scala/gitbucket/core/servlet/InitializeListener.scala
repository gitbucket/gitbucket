package gitbucket.core.servlet

import java.io.File

import akka.event.Logging
import com.typesafe.config.ConfigFactory
import gitbucket.core.GitBucketCoreModule
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.{ActivityService, SystemSettingsService}
import gitbucket.core.util.DatabaseConfig
import gitbucket.core.util.Directory._
import gitbucket.core.util.JDBCUtil._
import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.manager.JDBCVersionManager
import javax.servlet.{ServletContextListener, ServletContextEvent}
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import akka.actor.{Actor, Props, ActorSystem}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

/**
 * Initialize GitBucket system.
 * Update database schema and load plug-ins automatically in the context initializing.
 */
class InitializeListener extends ServletContextListener with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[InitializeListener])

  override def contextInitialized(event: ServletContextEvent): Unit = {
    val dataDir = event.getServletContext.getInitParameter("gitbucket.home")
    if(dataDir != null){
      System.setProperty("gitbucket.home", dataDir)
    }
    org.h2.Driver.load()

    Database() withTransaction { session =>
      val conn = session.conn
      val manager = new JDBCVersionManager(conn)

      // Check version
      val versionFile = new File(GitBucketHome, "version")

      if(versionFile.exists()){
        val version = FileUtils.readFileToString(versionFile, "UTF-8")
        if(version == "3.14"){
          // Initialization for GitBucket 3.14
          logger.info("Migration to GitBucket 4.x start")

          // Backup current data
          val dataMvFile = new File(GitBucketHome, "data.mv.db")
          if(dataMvFile.exists) {
            FileUtils.copyFile(dataMvFile, new File(GitBucketHome, "data.mv.db_3.14"))
          }
          val dataTraceFile = new File(GitBucketHome, "data.trace.db")
          if(dataTraceFile.exists) {
            FileUtils.copyFile(dataTraceFile, new File(GitBucketHome, "data.trace.db_3.14"))
          }

          // Change form
          manager.initialize()
          manager.updateVersion(GitBucketCoreModule.getModuleId, "4.0.0")
          conn.select("SELECT PLUGIN_ID, VERSION FROM PLUGIN"){ rs =>
            manager.updateVersion(rs.getString("PLUGIN_ID"), rs.getString("VERSION"))
          }
          conn.update("DROP TABLE PLUGIN")
          versionFile.delete()

          logger.info("Migration to GitBucket 4.x completed")

        } else {
          throw new Exception("GitBucket can't migrate from this version. Please update to 3.14 at first.")
        }
      }

      // Run normal migration
      logger.info("Start schema update")
      val solidbase = new Solidbase()
      solidbase.migrate(conn, Thread.currentThread.getContextClassLoader, DatabaseConfig.liquiDriver, GitBucketCoreModule)

      // Rescue code for users who updated from 3.14 to 4.0.0
      // https://github.com/gitbucket/gitbucket/issues/1227
      val currentVersion = manager.getCurrentVersion(GitBucketCoreModule.getModuleId)
      if(currentVersion == "4.0"){
        manager.updateVersion(GitBucketCoreModule.getModuleId, "4.0.0")
      }

      // Load plugins
      logger.info("Initialize plugins")
      PluginRegistry.initialize(event.getServletContext, loadSystemSettings(), conn)
    }

    // Start Quartz scheduler
    val system = ActorSystem("job", ConfigFactory.parseString(
      """
        |akka {
        |  quartz {
        |    schedules {
        |      Daily {
        |        expression = "0 0 0 * * ?"
        |      }
        |    }
        |  }
        |}
      """.stripMargin))

    val scheduler = QuartzSchedulerExtension(system)

    scheduler.schedule("Daily", system.actorOf(Props[DeleteOldActivityActor]), "DeleteOldActivity")
  }

  override def contextDestroyed(event: ServletContextEvent): Unit = {
    // Shutdown plugins
    PluginRegistry.shutdown(event.getServletContext, loadSystemSettings())
    // Close datasource
    Database.closeDataSource()
  }

}

class DeleteOldActivityActor extends Actor with SystemSettingsService with ActivityService {

  private val logger = Logging(context.system, this)

  def receive = {
    case s: String => {
      loadSystemSettings().activityLogLimit.foreach { limit =>
        if(limit > 0){
          Database() withTransaction { implicit session =>
            val rows = deleteOldActivities(limit)
            logger.info(s"Deleted ${rows} activity logs")
          }
        }
      }
    }
  }
}