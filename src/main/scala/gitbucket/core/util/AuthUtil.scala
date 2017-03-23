package gitbucket.core.util

import java.util.Base64
import javax.servlet.http.HttpServletResponse

/**
 * Provides HTTP (Basic) Authentication related functions.
 */
object AuthUtil {
  def requireAuth(response: HttpServletResponse): Unit = {
    response.setHeader("WWW-Authenticate", "BASIC realm=\"GitBucket\"")
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
  }

  def decodeAuthHeader(header: String): String = {
    try {
      new String(Base64.getDecoder.decode(header.substring(6)))
    } catch {
      case _: Throwable => ""
    }
  }
}
