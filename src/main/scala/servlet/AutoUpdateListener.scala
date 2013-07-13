package servlet

import java.io.File
import java.sql.Connection
import org.apache.commons.io.FileUtils
import javax.servlet.ServletContextEvent
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import util.Directory

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
      val in = Thread.currentThread.getContextClassLoader.getResourceAsStream(sqlPath)
      if(in != null){
        val sql = IOUtils.toString(in, "UTF-8")
        val stmt = conn.createStatement()
        try {
          logger.debug(sqlPath + "=" + sql)
          stmt.executeUpdate(sql)
        } finally {
          stmt.close()
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
    Version(1, 4),
    new Version(1, 3){
      override def update(conn: Connection): Unit = {
        super.update(conn)
        // Fix wiki repository configuration
        val rs = conn.createStatement.executeQuery("SELECT USER_NAME, REPOSITORY_NAME FROM REPOSITORY")
        while(rs.next){
          val wikidir = Directory.getWikiRepositoryDir(rs.getString("USER_NAME"), rs.getString("REPOSITORY_NAME"))
          val repository = org.eclipse.jgit.api.Git.open(wikidir).getRepository
          val config = repository.getConfig
          if(!config.getBoolean("http", "receivepack", false)){
            config.setBoolean("http", null, "receivepack", true)
            config.save
          }
          repository.close
        }
      }
    },
    Version(1, 2),
    Version(1, 1),
    Version(1, 0)
  )
  
  /**
   * The head version of BitBucket.
   */
  val headVersion = versions.head
  
  /**
   * The version file (GITBUCKET_HOME/version).
   */
  val versionFile = new File(Directory.GitBucketHome, "version")
  
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
 * Start H2 database and update schema automatically.
 */
class AutoUpdateListener extends org.h2.server.web.DbStarter {
  import AutoUpdate._
  private val logger = LoggerFactory.getLogger(classOf[AutoUpdateListener])
  
  override def contextInitialized(event: ServletContextEvent): Unit = {
    super.contextInitialized(event)
    logger.debug("H2 started")
    
    logger.debug("Start schema update")
    val conn = getConnection()
    try {
      val currentVersion = getCurrentVersion()
      if(currentVersion == headVersion){
        logger.debug("No update")
      } else {
        versions.takeWhile(_ != currentVersion).reverse.foreach(_.update(conn))
        FileUtils.writeStringToFile(versionFile, headVersion.versionString, "UTF-8")
        conn.commit()
        logger.debug("Updated from " + currentVersion.versionString + " to " + headVersion.versionString)
      }
    } catch {
      case ex: Throwable => {
        logger.error("Failed to schema update", ex)
        conn.rollback()
      }
    }
    logger.debug("End schema update")
  }
  
}