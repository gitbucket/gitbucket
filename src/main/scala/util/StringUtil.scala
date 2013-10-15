package util

import java.net.{URLDecoder, URLEncoder}
import org.mozilla.universalchardet.UniversalDetector
import util.ControlUtil._

object StringUtil {

  def sha1(value: String): String =
    defining(java.security.MessageDigest.getInstance("SHA-1")){ md =>
      md.update(value.getBytes)
      md.digest.map(b => "%02x".format(b)).mkString
    }

  def md5(value: String): String = {
    val md = java.security.MessageDigest.getInstance("MD5")
    md.update(value.getBytes)
    md.digest.map(b => "%02x".format(b)).mkString
  }

  def urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

  def urlDecode(value: String): String = URLDecoder.decode(value, "UTF-8")

  def splitWords(value: String): Array[String] = value.split("[ \\t　]+")

  def escapeHtml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  def convertFromByteArray(content: Array[Byte]): String = new String(content, detectEncoding(content))

  def detectEncoding(content: Array[Byte]): String =
    defining(new UniversalDetector(null)){ detector =>
      detector.handleData(content, 0, content.length)
      detector.dataEnd()
      detector.getDetectedCharset match {
        case null => "UTF-8"
        case e    => e
      }
    }
}
