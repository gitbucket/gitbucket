package gitbucket.core.service

import gitbucket.core.servlet.AutoUpdate
import gitbucket.core.util.{ControlUtil, DatabaseConfig, FileUtil}
import gitbucket.core.model.Profile._
import profile.simple._
import ControlUtil._
import java.sql.DriverManager
import org.apache.commons.io.FileUtils
import scala.util.Random
import java.io.File

trait ServiceSpecBase {

  def withTestDB[A](action: (Session) => A): A = {
    FileUtil.withTmpDir(new File(FileUtils.getTempDirectory(), Random.alphanumeric.take(10).mkString)){ dir =>
      val (url, user, pass) = (DatabaseConfig.url(Some(dir.toString)), DatabaseConfig.user, DatabaseConfig.password)
      org.h2.Driver.load()
      using(DriverManager.getConnection(url, user, pass)){ conn =>
        AutoUpdate.versions.reverse.foreach(_.update(conn, Thread.currentThread.getContextClassLoader))
      }
      Database.forURL(url, user, pass).withSession { session =>
        action(session)
      }
    }
  }

}
