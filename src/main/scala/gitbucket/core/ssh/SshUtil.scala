package gitbucket.core.ssh

import java.security.PublicKey
import java.util.Base64

import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.util.buffer.ByteArrayBuffer
import org.eclipse.jgit.lib.Constants
import org.slf4j.LoggerFactory

object SshUtil {

  private val logger = LoggerFactory.getLogger(SshUtil.getClass)

  def str2PublicKey(key: String): Option[PublicKey] = {
    // TODO RFC 4716 Public Key is not supported...
    val parts = key.split(" ")
    if (parts.size < 2) {
      logger.debug(s"Invalid PublicKey Format: ${key}")
      None
    } else {
      try {
        val encodedKey = parts(1)
        val decode = Base64.getDecoder.decode(Constants.encodeASCII(encodedKey))
        Some(new ByteArrayBuffer(decode).getRawPublicKey)
      } catch {
        case e: Throwable =>
          logger.debug(e.getMessage, e)
          None
      }
    }
  }

  def fingerPrint(key: String): Option[String] =
    str2PublicKey(key) map KeyUtils.getFingerPrint

}
