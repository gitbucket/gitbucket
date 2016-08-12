package gitbucket.core.util

import java.net.{URLDecoder, URLEncoder}
import org.mozilla.universalchardet.UniversalDetector
import ControlUtil._
import org.apache.commons.io.input.BOMInputStream
import org.apache.commons.io.IOUtils
import scala.util.control.Exception._

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

  def urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

  def urlDecode(value: String): String = URLDecoder.decode(value, "UTF-8")

  def splitWords(value: String): Array[String] = value.split("[ \\t　]+")

  def isInteger(value: String): Boolean = allCatch opt { value.toInt } map(_ => true) getOrElse(false)

  def escapeHtml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  /**
   * Make string from byte array. Character encoding is detected automatically by [[StringUtil.detectEncoding]].
   * And if given bytes contains UTF-8 BOM, it's removed from returned string.
   */
  def convertFromByteArray(content: Array[Byte]): String =
    IOUtils.toString(new BOMInputStream(new java.io.ByteArrayInputStream(content)), detectEncoding(content))

  def detectEncoding(content: Array[Byte]): String =
    defining(new UniversalDetector(null)){ detector =>
      detector.handleData(content, 0, content.length)
      detector.dataEnd()
      detector.getDetectedCharset match {
        case null => "UTF-8"
        case e    => e
      }
    }

  /**
   * Converts line separator in the given content.
   *
   * @param content the content
   * @param lineSeparator "LF" or "CRLF"
   * @return the converted content
   */
  def convertLineSeparator(content: String, lineSeparator: String): String = {
    val lf = content.replace("\r\n", "\n").replace("\r", "\n")
    if(lineSeparator == "CRLF"){
      lf.replace("\n", "\r\n")
    } else {
      lf
    }
  }

  /**
   * Appends LF if the given string does not end with LF.
   *
   * @param content the content
   * @param lineSeparator "LF" or "CRLF"
   * @return the converted content
   */
  def appendNewLine(content: String, lineSeparator: String): String = {
    if(lineSeparator == "CRLF") {
      if (content.endsWith("\r\n")) content else content + "\r\n"
    } else {
      if (content.endsWith("\n")) content else content + "\n"
    }
  }

  /**
   * Extract issue id like ```#issueId``` from the given message.
   *
   *@param message the message which may contains issue id
   * @return the iterator of issue id
   */
  def extractIssueId(message: String): Iterator[String] =
    "(^|\\W)#(\\d+)(\\W|$)".r.findAllIn(message).matchData.map(_.group(2))

  /**
   * Extract close issue id like ```close #issueId ``` from the given message.
   *
   * @param message the message which may contains close command
   * @return the iterator of issue id
   */
  def extractCloseId(message: String): Iterator[String] =
    "(?i)(?<!\\w)(?:fix(?:e[sd])?|resolve[sd]?|close[sd]?)\\s+#(\\d+)(?!\\w)".r.findAllIn(message).matchData.map(_.group(1))

}
