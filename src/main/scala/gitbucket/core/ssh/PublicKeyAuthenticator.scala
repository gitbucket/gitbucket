package gitbucket.core.ssh

import gitbucket.core.service.SshKeyService
import gitbucket.core.servlet.Database
import org.apache.sshd.server.PublickeyAuthenticator
import org.apache.sshd.server.session.ServerSession
import java.security.PublicKey
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.service.AccountService
import gitbucket.core.util.LDAPUtil

class PublicKeyAuthenticator extends PublickeyAuthenticator with SshKeyService with SystemSettingsService{

  override def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean = {
    Database() withSession { implicit session =>
      val settings = loadSystemSettings()
      if (settings.ldapAuthentication) {
        AccountService.getAccountByUserName(username) match {
          case Some(x) => 
            LDAPUtil.authenticatePubkey(settings.ldap.get, username, key) match {
              case Right(true) => return true
              case _ => ;
            }
          case _ => System.out.println("user not exist =" + username);
        }
      }
      getPublicKeys(username).exists { sshKey =>
        SshUtil.str2PublicKey(sshKey.publicKey) match {
          case Some(publicKey) => key.equals(publicKey)
          case _ => false
        }
      }
    }
  }

}
