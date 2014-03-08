package ssh

import org.apache.sshd.server.PublickeyAuthenticator
import org.slf4j.LoggerFactory
import org.apache.sshd.server.session.ServerSession
import java.security.PublicKey
import org.apache.commons.codec.binary.Base64
import org.apache.sshd.common.util.Buffer
import org.eclipse.jgit.lib.Constants


object DummyData {
  val userPublicKeys = List(
    "ssh-rsa AAB3NzaC1yc2EAAAADAQABAAABAQDRzuX0WtSLzCY45nEhfFDPXzYGmvQdqnOgOUY4yGL5io/2ztyUvJdhWowkyakeoPxVk/jIP7Tu8Are5TuSD+fJp7aUbZW2CYOEsxo8cwndh/ezIX6RFjlu+xvKvZ8G7BtFLlLCcnza9uB+uEAyPH5HvGQLdV7dXctLfFqXPTr1p1RjSI7Noubm+vN4n9108rILd32MlhQiToXjL4HKWWwmppaln6bEsonOQW4/GieRjQeyWDkbVekIofnedjWl4+W0kAA+WosNwRFShgsaJLfU964HT/cGjK5auqOG+nATY0suECnxAK+5Wb6jXXYNmKiIMHypeXG1Qy2wMyMB1Gq9 tanacasino-local",
    "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDRzuX0WtSLzCY45nEhfFDPXzYGmvQdqnOgOUY4yGL5io/2ztyUvJdhWowkyakeoPxVk/jIP7Tu8Are5TuSD+fJp7aUbZW2CYOEsxo8cwndh/ezIX6RFjlu+xvKvZ8G7BtFLlLCcnza9uB+uEAyPH5HvGQLdV7dXctLfFqXPTr1p1RjSI7Noubm+vN4n9108rILd32MlhQiToXjL4HKWWwmppaln6bEsonOQW4/GieRjQeyWDkbVekIofnedjWl4+W0kAA+WosNwRFShgsaJLfU964HT/cGjK5auqOG+nATY0suECnxAK+5Wb6jXXYNmKiIMHypeXG1Qy2wMyMB1Gq9 tanacasino-local",
    "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAIEAxBEtdwpE5PaClaEq2WY369einovW2ZUOXFKndY4z8RN7S3H4G1whMJVIsj2lrw1k+ranzNmOEHFoRKO0/XIE/2mSaGOawKG76vKEA/q7A0Zw8hMcdIBPaqMhrb/K7KyJiJtcvARelO76mUGv9ucA6DqvsuPjGalqhdp9eSq+1VE= naoki@your-4v4sjfo73c"
  )
}


class PublicKeyAuthenticator extends PublickeyAuthenticator {
  private val logger = LoggerFactory.getLogger(classOf[PublicKeyAuthenticator])

  override def authenticate(username: String, key: PublicKey, session: ServerSession): Boolean = {
    // TODO userPublicKeys is read from DB and Users register this public key string list on Account Profile view
    DummyData.userPublicKeys.exists(str => str2PublicKey(str) match {
      case Some(publicKey) => key.equals(publicKey)
      case _ => false
    })
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
