package gitbucket.core.servlet

import javax.servlet.http.{HttpSessionEvent, HttpSessionListener}
import gitbucket.core.util.Directory
import org.apache.commons.io.FileUtils
import Directory._

/**
 * Removes session associated temporary files when session is destroyed.
 */
class SessionCleanupListener extends HttpSessionListener {

  def sessionCreated(se: HttpSessionEvent): Unit = {}

  def sessionDestroyed(se: HttpSessionEvent): Unit = FileUtils.deleteDirectory(getTemporaryDir(se.getSession.getId))

}
