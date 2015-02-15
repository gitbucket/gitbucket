package ssh

import org.apache.sshd.server.PublickeyAuthenticator
import org.apache.sshd.server.session.ServerSession
import java.security.PublicKey
import service.SshKeyService
import servlet.Database

class PublicKeyAuthenticator extends PublickeyAuthenticator with SshKeyService {

  override def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean = {
    Database() withSession { implicit session =>
      getPublicKeys(username).exists { sshKey =>
        SshUtil.str2PublicKey(sshKey.publicKey) match {
          case Some(publicKey) => key.equals(publicKey)
          case _ => false
        }
      }
    }
  }

}
