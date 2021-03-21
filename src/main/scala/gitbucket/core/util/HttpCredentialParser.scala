package gitbucket.core.util

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

import gitbucket.core.model.BasicAuthCredentials

object HttpCredentialParser {
  def parseBasicAuth(encoded: String): Option[BasicAuthCredentials] =
    if (encoded.startsWith("Basic ")) {
      val encodedUserAndPassword = encoded.substring(6).trim()

      if (encodedUserAndPassword.length <= 0) {
        None
      } else {
        val decodedUserAndPassword = Base64.getDecoder.decode(encodedUserAndPassword)
        val userAndPassword = new String(decodedUserAndPassword, UTF_8)
        val parts = userAndPassword.split(":", 2)

        if (parts.length < 2) {
          Some(BasicAuthCredentials(parts(0), ""))
        } else {
          Some(BasicAuthCredentials(parts(0), parts(1)))
        }
      }
    } else {
      None
    }
}
