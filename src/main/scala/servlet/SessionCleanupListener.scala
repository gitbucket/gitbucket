package servlet

import javax.servlet.http.{HttpSessionEvent, HttpSessionListener}
import app.FileUploadControllerBase

/**
 * Removes session associated temporary files when session is destroyed.
 */
class SessionCleanupListener extends HttpSessionListener with FileUploadControllerBase {

  def sessionCreated(se: HttpSessionEvent): Unit = {}

  def sessionDestroyed(se: HttpSessionEvent): Unit = removeTemporaryFiles()(se.getSession)

}
