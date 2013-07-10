package servlet

import util.FileUploadUtil
import javax.servlet.http.{HttpSessionEvent, HttpSessionListener}

/**
 * Removes session associated temporary files when session is destroyed.
 */
class SessionCleanupListener extends HttpSessionListener {

  def sessionCreated(se: HttpSessionEvent): Unit = {}

  def sessionDestroyed(se: HttpSessionEvent): Unit = {
    FileUploadUtil.removeTemporaryFiles()(se.getSession)
  }

}
