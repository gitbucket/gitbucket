package gitbucket.core.plugin

import slick.jdbc.JdbcBackend.Session

/**
 * Provides Slick Session to Plug-ins.
 */
object Sessions {
  val sessions = new ThreadLocal[Session]
  implicit def session: Session = sessions.get()
}
