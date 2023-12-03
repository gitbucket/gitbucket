package gitbucket.core.servlet

import gitbucket.core.util.Directory.*
import org.apache.commons.io.FileUtils

import javax.servlet.http.{HttpSessionEvent, HttpSessionListener}

/**
 * Removes session associated temporary files when session is destroyed.
 */
class SessionCleanupListener extends HttpSessionListener {

  override def sessionCreated(se: HttpSessionEvent): Unit = {}

  override def sessionDestroyed(se: HttpSessionEvent): Unit =
    FileUtils.deleteDirectory(getTemporaryDir(se.getSession.getId))

}
