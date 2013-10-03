package servlet

import java.io.File
import java.sql.{DriverManager, Connection}
import org.apache.commons.io.FileUtils
import javax.servlet.{ServletContext, ServletContextListener, ServletContextEvent}
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import util.Directory._
import util.ControlUtil._
import org.eclipse.jgit.api.Git

object AutoUpdate {
  
  /**
   * Version of GitBucket
   * 
   * @param majorVersion the major version
   * @param minorVersion the minor version
   */
  case class Version(majorVersion: Int, minorVersion: Int){
    
    private val logger = LoggerFactory.getLogger(classOf[servlet.AutoUpdate.Version])
    
    /**
     * Execute update/MAJOR_MINOR.sql to update schema to this version.
     * If corresponding SQL file does not exist, this method do nothing.
     */
    def update(conn: Connection): Unit = {
      val sqlPath = s"update/${majorVersion}_${minorVersion}.sql"

      using(Thread.currentThread.getContextClassLoader.getResourceAsStream(sqlPath)){ in =>
        if(in != null){
          val sql = IOUtils.toString(in, "UTF-8")
          using(conn.createStatement()){ stmt =>
            logger.debug(sqlPath + "=" + sql)
            stmt.executeUpdate(sql)
          }
        }
      }
    }
    
    /**
     * MAJOR.MINOR
     */
    val versionString = s"${majorVersion}.${minorVersion}"
  }

  /**
   * The history of versions. A head of this sequence is the current BitBucket version.
   */
  val versions = Seq(
    Version(1, 6),
    Version(1, 5),
    Version(1, 4),
    new Version(1, 3){
      override def update(conn: Connection): Unit = {
        super.update(conn)
        // Fix wiki repository configuration
        using(conn.createStatement.executeQuery("SELECT USER_NAME, REPOSITORY_NAME FROM REPOSITORY")){ rs =>
          while(rs.next){
            using(Git.open(getWikiRepositoryDir(rs.getString("USER_NAME"), rs.getString("REPOSITORY_NAME")))){ git =>
              defining(git.getRepository.getConfig){ config =>
                if(!config.getBoolean("http", "receivepack", false)){
                  config.setBoolean("http", null, "receivepack", true)
                  config.save
                }
              }
            }
          }
        }
      }
    },
    Version(1, 2),
    Version(1, 1),
    Version(1, 0),
    Version(0, 0)
  )
  
  /**
   * The head version of BitBucket.
   */
  val headVersion = versions.head
  
  /**
   * The version file (GITBUCKET_HOME/version).
   */
  val versionFile = new File(GitBucketHome, "version")
  
  /**
   * Returns the current version from the version file.
   */
  def getCurrentVersion(): Version = {
    if(versionFile.exists){
      FileUtils.readFileToString(versionFile, "UTF-8").split("\\.") match {
        case Array(majorVersion, minorVersion) => {
          versions.find { v => 
            v.majorVersion == majorVersion.toInt && v.minorVersion == minorVersion.toInt
          }.getOrElse(Version(0, 0))
        }
        case _ => Version(0, 0)
      }
    } else {
      Version(0, 0)
    }
    
  }  
  
}

/**
 * Update database schema automatically in the context initializing.
 */
class AutoUpdateListener extends ServletContextListener {
  import AutoUpdate._
  private val logger = LoggerFactory.getLogger(classOf[AutoUpdateListener])
  
  override def contextInitialized(event: ServletContextEvent): Unit = {
    org.h2.Driver.load()
    event.getServletContext.setInitParameter("db.url", s"jdbc:h2:${DatabaseHome}")

    logger.debug("Start schema update")
    defining(getConnection(event.getServletContext)){ conn =>
      try {
        defining(getCurrentVersion()){ currentVersion =>
          if(currentVersion == headVersion){
            logger.debug("No update")
          } else if(!versions.contains(currentVersion)){
            logger.warn(s"Skip migration because ${currentVersion.versionString} is illegal version.")
          } else {
            versions.takeWhile(_ != currentVersion).reverse.foreach(_.update(conn))
            FileUtils.writeStringToFile(versionFile, headVersion.versionString, "UTF-8")
            conn.commit()
            logger.debug(s"Updated from ${currentVersion.versionString} to ${headVersion.versionString}")
          }
        }
      } catch {
        case ex: Throwable => {
          logger.error("Failed to schema update", ex)
          ex.printStackTrace()
          conn.rollback()
        }
      }
    }
    logger.debug("End schema update")
  }

  def contextDestroyed(sce: ServletContextEvent): Unit = {
    // Nothing to do.
  }

  private def getConnection(servletContext: ServletContext): Connection =
    DriverManager.getConnection(
      servletContext.getInitParameter("db.url"),
      servletContext.getInitParameter("db.user"),
      servletContext.getInitParameter("db.password"))

}