package gitbucket.core.util

import java.sql.Connection

import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import ControlUtil._

case class Version(majorVersion: Int, minorVersion: Int) {

  private val logger = LoggerFactory.getLogger(classOf[Version])

  /**
   * Execute update/MAJOR_MINOR.sql to update schema to this version.
   * If corresponding SQL file does not exist, this method do nothing.
   */
  def update(conn: Connection, cl: ClassLoader): Unit = {
    val sqlPath = s"update/${majorVersion}_${minorVersion}.sql"

    using(cl.getResourceAsStream(sqlPath)){ in =>
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

object Versions {

  private val logger = LoggerFactory.getLogger(Versions.getClass)

  def update(conn: Connection, headVersion: Version, currentVersion: Version, versions: Seq[Version], cl: ClassLoader)
            (save: Connection => Unit): Unit = {
    logger.debug("Start schema update")
    try {
      if(currentVersion == headVersion){
        logger.debug("No update")
      } else if(currentVersion.versionString != "0.0" && !versions.contains(currentVersion)){
        logger.warn(s"Skip migration because ${currentVersion.versionString} is illegal version.")
      } else {
        versions.takeWhile(_ != currentVersion).reverse.foreach(_.update(conn, cl))
        save(conn)
        logger.debug(s"Updated from ${currentVersion.versionString} to ${headVersion.versionString}")
      }
    } catch {
      case ex: Throwable => {
        logger.error("Failed to schema update", ex)
        ex.printStackTrace()
        conn.rollback()
      }
    }
    logger.debug("End schema update")
  }

}

