package ssh

import org.apache.sshd.server.{PublickeyAuthenticator, PasswordAuthenticator}
import org.slf4j.LoggerFactory
import org.apache.sshd.server.session.ServerSession
import java.security.PublicKey


class PublicKeyAuthenticator extends PublickeyAuthenticator {
  override def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean = {
    // TODO Implements PublicKeyAuthenticator
    true
  }
}

// always true authenticator...
class MyPasswordAuthenticator extends PasswordAuthenticator {
  private val logger = LoggerFactory.getLogger(classOf[MyPasswordAuthenticator])

  override def authenticate(username: String, password: String, session: ServerSession): Boolean = {
    logger.info("noop authenticate!!!")
    true
  }
}