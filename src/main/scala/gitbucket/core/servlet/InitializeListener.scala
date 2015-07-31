package gitbucket.core.servlet

import akka.event.Logging
import com.typesafe.config.ConfigFactory
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.{ActivityService, SystemSettingsService}
import org.apache.commons.io.FileUtils
import javax.servlet.{ServletContextListener, ServletContextEvent}
import org.slf4j.LoggerFactory
import gitbucket.core.util.Versions
import akka.actor.{Actor, Props, ActorSystem}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import AutoUpdate._
import gitbucket.core.util.Directory._
import slick.jdbc.JdbcBackend.{Database => SlickDatabase, Session}
import gitbucket.core.util.DatabaseConfig
import java.sql._
import java.io.IOException

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

    val oldH2DataFile = new java.io.File(GitBucketHome, "data.mv.db")
    if(oldH2DataFile.exists){
      val db = SlickDatabase.forURL(
        url      = DatabaseConfig.url.replace(";MV_STORE=FALSE", ""),
        driver   = DatabaseConfig.driver,
        user     = DatabaseConfig.user, 
        password = DatabaseConfig.password
      )

      val conn = DriverManager.getConnection(
        DatabaseConfig.url.replace(";MV_STORE=FALSE", ""),
        DatabaseConfig.user,
        DatabaseConfig.password
      )

      val exportFile = new java.io.File(GitBucketHome, "export.sql")

      try {
        conn.prepareStatement(s"SCRIPT TO '${exportFile.getAbsolutePath}'").execute()
      } finally {
        conn.close()
      }
      logger.info(s"[Migration]Exported to ${exportFile.getAbsolutePath}")

      Database() withSession { session =>
        val conn = session.conn
        conn.prepareStatement(s"RUNSCRIPT FROM '${exportFile.getAbsolutePath}'").execute()
        logger.info(s"[Migration]Imported from ${exportFile.getAbsolutePath}")
      }

      if(!oldH2DataFile.delete()){
        throw new IOException(s"[Migration]Failed to delete ${oldH2DataFile.getAbsolutePath}")
      }
    }

    Database() withTransaction { session =>
      val conn = session.conn

      // Migration
      logger.debug("Start schema update")
      Versions.update(conn, headVersion, getCurrentVersion(), versions, Thread.currentThread.getContextClassLoader){ conn =>
        FileUtils.writeStringToFile(versionFile, headVersion.versionString, "UTF-8")
      }

      // Load plugins
      logger.debug("Initialize plugins")
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
