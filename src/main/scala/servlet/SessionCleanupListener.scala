package servlet

import javax.servlet.http.{HttpSessionEvent, HttpSessionListener}
import org.apache.commons.io.FileUtils
import util.Directory._

/**
 * Removes session associated temporary files when session is destroyed.
 */
class SessionCleanupListener extends HttpSessionListener {

  def sessionCreated(se: HttpSessionEvent): Unit = {}

  def sessionDestroyed(se: HttpSessionEvent): Unit = FileUtils.deleteDirectory(getTemporaryDir(se.getSession.getId))

}
