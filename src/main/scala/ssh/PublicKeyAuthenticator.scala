package ssh

import org.apache.sshd.server.PublickeyAuthenticator
import org.slf4j.LoggerFactory
import org.apache.sshd.server.session.ServerSession
import java.security.PublicKey
import org.apache.commons.codec.binary.Base64
import org.apache.sshd.common.util.Buffer
import org.eclipse.jgit.lib.Constants
import service.SshKeyService
import servlet.Database
import javax.servlet.ServletContext

class PublicKeyAuthenticator(context: ServletContext) extends PublickeyAuthenticator with SshKeyService {
  private val logger = LoggerFactory.getLogger(classOf[PublicKeyAuthenticator])

  override def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean = {
    Database(context) withTransaction {
      getPublicKeys(username).exists { sshKey =>
        str2PublicKey(sshKey.publicKey) match {
          case Some(publicKey) => key.equals(publicKey)
          case _ => false
        }
      }
    }
  }

  private def str2PublicKey(key: String): Option[PublicKey] = {
    // TODO RFC 4716 Public Key is not supported...
    val parts = key.split(" ")
    if (parts.size < 2) {
      logger.debug(s"Invalid PublicKey Format: key")
      return None
    }
    try {
      val encodedKey = parts(1)
      val decode = Base64.decodeBase64(Constants.encodeASCII(encodedKey))
      Some(new Buffer(decode).getRawPublicKey)
    } catch {
      case e: Throwable =>
        logger.debug(e.getMessage, e)
        None
    }
  }

}
