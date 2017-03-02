package gitbucket.core.ssh

import java.security.PublicKey

import gitbucket.core.service.SshKeyService
import gitbucket.core.servlet.Database
import gitbucket.core.model.Profile.profile.blockingApi._
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.common.AttributeStore
import org.slf4j.LoggerFactory

object PublicKeyAuthenticator {
  // put in the ServerSession here to be read by GitCommand later
  private val userNameSessionKey = new AttributeStore.AttributeKey[String]

  def putUserName(serverSession: ServerSession, userName: String):Unit =
    serverSession.setAttribute(userNameSessionKey, userName)

  def getUserName(serverSession: ServerSession):Option[String] =
    Option(serverSession.getAttribute(userNameSessionKey))
}

class PublicKeyAuthenticator(genericUser: String) extends PublickeyAuthenticator with SshKeyService {
  private val logger = LoggerFactory.getLogger(classOf[PublicKeyAuthenticator])

  override def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean =
    if (username == genericUser) authenticateGenericUser(username, key, session, genericUser)
    else                         authenticateLoginUser(username, key, session)

  private def authenticateLoginUser(username: String, key: PublicKey, session: ServerSession): Boolean = {
    val authenticated =
      Database()
      .withSession { implicit dbSession => getPublicKeys(username) }
      .map(_.publicKey)
      .flatMap(SshUtil.str2PublicKey)
      .contains(key)
    if (authenticated) {
      logger.info(s"authentication as ssh user ${username} succeeded")
      PublicKeyAuthenticator.putUserName(session, username)
    }
    else {
      logger.info(s"authentication as ssh user ${username} failed")
    }
    authenticated
  }

  private def authenticateGenericUser(username: String, key: PublicKey, session: ServerSession, genericUser: String): Boolean = {
    // find all users having the key we got from ssh
    val possibleUserNames =
      Database()
      .withSession { implicit dbSession => getAllKeys() }
      .filter { sshKey =>
        SshUtil.str2PublicKey(sshKey.publicKey).exists(_ == key)
      }
      .map(_.userName)
      .distinct
    // determine the user - if different accounts share the same key, tough luck
    val uniqueUserName =
      possibleUserNames match {
        case List()     =>
          logger.info(s"authentication as generic user ${genericUser} failed, public key not found")
          None
        case List(name) =>
          logger.info(s"authentication as generic user ${genericUser} succeeded, identified ${name}")
          Some(name)
        case _          =>
          logger.info(s"authentication as generic user ${genericUser} failed, public key is ambiguous")
          None
      }
    uniqueUserName.foreach(PublicKeyAuthenticator.putUserName(session, _))
    uniqueUserName.isDefined
  }
}
