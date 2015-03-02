package gitbucket.core.plugin

import scala.slick.jdbc.JdbcBackend.Session

/**
 * Provides Slick Session to Plug-ins.
 */
object Sessions {
  val sessions = new ThreadLocal[Session]
  implicit def session: Session = sessions.get()
}
