package ssh

import java.security.PublicKey
import org.slf4j.LoggerFactory
import org.apache.commons.codec.binary.Base64
import org.eclipse.jgit.lib.Constants
import org.apache.sshd.common.util.{KeyUtils, Buffer}

object SshUtil {

  private val logger = LoggerFactory.getLogger(SshUtil.getClass)

  def str2PublicKey(key: String): Option[PublicKey] = {
    // TODO RFC 4716 Public Key is not supported...
    val parts = key.split(" ")
    if (parts.size < 2) {
      logger.debug(s"Invalid PublicKey Format: ${key}")
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

  def fingerPrint(key: String): Option[String] = str2PublicKey(key) match {
    case Some(publicKey) => Some(KeyUtils.getFingerPrint(publicKey))
    case None => None
  }

}
