package ssh

import org.apache.sshd.server.{PublickeyAuthenticator, PasswordAuthenticator}
import org.slf4j.LoggerFactory
import org.apache.sshd.server.session.ServerSession
import java.security.{KeyFactory, PublicKey}
import org.apache.commons.codec.binary.Base64
import java.security.spec.X509EncodedKeySpec
import org.apache.sshd.common.util.Buffer


class PublicKeyAuthenticator extends PublickeyAuthenticator {
  private val logger = LoggerFactory.getLogger(classOf[PublicKeyAuthenticator])

  override def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean = {
    // TODO this string is read from DB and Users register this public key string on Account Profile view
    val testAuthkey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDRzuX0WtSLzCY45nEhfFDPXzYGmvQdqnOgOUY4yGL5io/2ztyUvJdhWowkyakeoPxVk/jIP7Tu8Are5TuSD+fJp7aUbZW2CYOEsxo8cwndh/ezIX6RFjlu+xvKvZ8G7BtFLlLCcnza9uB+uEAyPH5HvGQLdV7dXctLfFqXPTr1p1RjSI7Noubm+vN4n9108rILd32MlhQiToXjL4HKWWwmppaln6bEsonOQW4/GieRjQeyWDkbVekIofnedjWl4+W0kAA+WosNwRFShgsaJLfU964HT/cGjK5auqOG+nATY0suECnxAK+5Wb6jXXYNmKiIMHypeXG1Qy2wMyMB1Gq9 tanacasino-local"
    toPublicKey(testAuthkey) match {
      case Some(publicKey) => key.equals(publicKey)
      case _ => false
    }
  }

  private def toPublicKey(key: String): Option[PublicKey] = {
    try {
      val parts = key.split(" ")
      val encodedKey = key.split(" ")(1)
      val decode = Base64.decodeBase64(encodedKey)
      Some(new Buffer(decode).getRawPublicKey)
    } catch {
      case e: Throwable => {
        logger.error(e.getMessage, e)
        None
      }
    }
  }
}
