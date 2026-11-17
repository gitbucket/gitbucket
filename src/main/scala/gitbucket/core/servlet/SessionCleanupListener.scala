package gitbucket.core.servlet

import gitbucket.core.util.Directory.*
import org.apache.commons.io.FileUtils

import javax.servlet.http.{HttpSessionEvent, HttpSessionIdListener, HttpSessionListener}

/**
 * Removes session associated temporary files when session is destroyed,
 * and moves them along when the session ID changes (on sign-in).
 */
class SessionCleanupListener extends HttpSessionListener with HttpSessionIdListener {

  override def sessionCreated(se: HttpSessionEvent): Unit = {}

  override def sessionDestroyed(se: HttpSessionEvent): Unit =
    FileUtils.deleteDirectory(getTemporaryDir(se.getSession.getId))

  override def sessionIdChanged(se: HttpSessionEvent, oldSessionId: String): Unit = {
    val oldDir = getTemporaryDir(oldSessionId)
    if (oldDir.exists) {
      FileUtils.moveDirectory(oldDir, getTemporaryDir(se.getSession.getId))
    }
  }

}
