package ssh

import org.apache.sshd.server.PublickeyAuthenticator
import org.apache.sshd.server.session.ServerSession
import java.security.PublicKey
import service.SshKeyService
import servlet.Database
import javax.servlet.ServletContext

class PublicKeyAuthenticator(context: ServletContext) extends PublickeyAuthenticator with SshKeyService {

  override def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean = {
    Database(context) withSession { implicit session =>
      getPublicKeys(username).exists { sshKey =>
        SshUtil.str2PublicKey(sshKey.publicKey) match {
          case Some(publicKey) => key.equals(publicKey)
          case _ => false
        }
      }
    }
  }

}
