package gitbucket.core.servlet

import java.io.File
import java.sql.{DriverManager, Connection}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.util._
import org.apache.commons.io.FileUtils
import javax.servlet.{ServletContextListener, ServletContextEvent}
import org.slf4j.LoggerFactory
import Directory._
import ControlUtil._
import JDBCUtil._
import org.eclipse.jgit.api.Git
import gitbucket.core.util.Versions
import gitbucket.core.util.Directory

object AutoUpdate {

  /**
   * The history of versions. A head of this sequence is the current BitBucket version.
   */
  val versions = Seq(
    new Version(3, 4),
    new Version(3, 3),
    new Version(3, 2),
    new Version(3, 1),
    new Version(3, 0),
    new Version(2, 8),
    new Version(2, 7) {
      override def update(conn: Connection, cl: ClassLoader): Unit = {
        super.update(conn, cl)
        conn.select("SELECT * FROM REPOSITORY"){ rs =>
          // Rename attached files directory from /issues to /comments
          val userName = rs.getString("USER_NAME")
          val repoName = rs.getString("REPOSITORY_NAME")
          defining(Directory.getAttachedDir(userName, repoName)){ newDir =>
            val oldDir = new File(newDir.getParentFile, "issues")
            if(oldDir.exists && oldDir.isDirectory){
              oldDir.renameTo(newDir)
            }
          }
          // Update ORIGIN_USER_NAME and ORIGIN_REPOSITORY_NAME if it does not exist
          val originalUserName = rs.getString("ORIGIN_USER_NAME")
          val originalRepoName = rs.getString("ORIGIN_REPOSITORY_NAME")
          if(originalUserName != null && originalRepoName != null){
            if(conn.selectInt("SELECT COUNT(*) FROM REPOSITORY WHERE USER_NAME = ? AND REPOSITORY_NAME = ?",
              originalUserName, originalRepoName) == 0){
              conn.update("UPDATE REPOSITORY SET ORIGIN_USER_NAME = NULL, ORIGIN_REPOSITORY_NAME = NULL " +
                "WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName)
            }
          }
          // Update PARENT_USER_NAME and PARENT_REPOSITORY_NAME if it does not exist
          val parentUserName = rs.getString("PARENT_USER_NAME")
          val parentRepoName = rs.getString("PARENT_REPOSITORY_NAME")
          if(parentUserName != null && parentRepoName != null){
            if(conn.selectInt("SELECT COUNT(*) FROM REPOSITORY WHERE USER_NAME = ? AND REPOSITORY_NAME = ?",
              parentUserName, parentRepoName) == 0){
              conn.update("UPDATE REPOSITORY SET PARENT_USER_NAME = NULL, PARENT_REPOSITORY_NAME = NULL " +
                "WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName)
            }
          }
        }
      }
    },
    new Version(2, 6),
    new Version(2, 5),
    new Version(2, 4),
    new Version(2, 3) {
      override def update(conn: Connection, cl: ClassLoader): Unit = {
        super.update(conn, cl)
        conn.select("SELECT ACTIVITY_ID, ADDITIONAL_INFO FROM ACTIVITY WHERE ACTIVITY_TYPE='push'"){ rs =>
          val curInfo = rs.getString("ADDITIONAL_INFO")
          val newInfo = curInfo.split("\n").filter(_ matches "^[0-9a-z]{40}:.*").mkString("\n")
          if (curInfo != newInfo) {
            conn.update("UPDATE ACTIVITY SET ADDITIONAL_INFO = ? WHERE ACTIVITY_ID = ?", newInfo, rs.getInt("ACTIVITY_ID"))
          }
        }
        ignore {
          FileUtils.deleteDirectory(Directory.getPluginCacheDir())
          //FileUtils.deleteDirectory(new File(Directory.PluginHome))
        }
      }
    },
    new Version(2, 2),
    new Version(2, 1),
    new Version(2, 0){
      override def update(conn: Connection, cl: ClassLoader): Unit = {
        import eu.medsea.mimeutil.{MimeUtil2, MimeType}

        val mimeUtil = new MimeUtil2()
        mimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.MagicMimeMimeDetector")

        super.update(conn, cl)
        conn.select("SELECT USER_NAME, REPOSITORY_NAME FROM REPOSITORY"){ rs =>
          defining(Directory.getAttachedDir(rs.getString("USER_NAME"), rs.getString("REPOSITORY_NAME"))){ dir =>
            if(dir.exists && dir.isDirectory){
              dir.listFiles.foreach { file =>
                if(file.getName.indexOf('.') < 0){
                  val mimeType = MimeUtil2.getMostSpecificMimeType(mimeUtil.getMimeTypes(file, new MimeType("application/octet-stream"))).toString
                  if(mimeType.startsWith("image/")){
                    file.renameTo(new File(file.getParent, file.getName + "." + mimeType.split("/")(1)))
                  }
                }
              }
            }
          }
        }
      }
    },
    Version(1, 13),
    Version(1, 12),
    Version(1, 11),
    Version(1, 10),
    Version(1, 9),
    Version(1, 8),
    Version(1, 7),
    Version(1, 6),
    Version(1, 5),
    Version(1, 4),
    new Version(1, 3){
      override def update(conn: Connection, cl: ClassLoader): Unit = {
        super.update(conn, cl)
        // Fix wiki repository configuration
        conn.select("SELECT USER_NAME, REPOSITORY_NAME FROM REPOSITORY"){ rs =>
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
  lazy val versionFile = new File(GitBucketHome, "version")

  /**
   * Returns the current version from the version file.
   */
  def getCurrentVersion(): Version = {
    if(versionFile.exists){
      FileUtils.readFileToString(versionFile, "UTF-8").trim.split("\\.") match {
        case Array(majorVersion, minorVersion) => {
          versions.find { v =>
            v.majorVersion == majorVersion.toInt && v.minorVersion == minorVersion.toInt
          }.getOrElse(Version(0, 0))
        }
        case _ => Version(0, 0)
      }
    } else Version(0, 0)
  }

}