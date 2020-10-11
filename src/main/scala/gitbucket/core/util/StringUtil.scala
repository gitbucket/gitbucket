package gitbucket.core.util

import java.net.{URLDecoder, URLEncoder}
import java.security.SecureRandom
import java.util.{Base64, UUID}

import org.mozilla.universalchardet.UniversalDetector
import SyntaxSugars._
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.apache.commons.io.input.BOMInputStream
import org.apache.commons.io.IOUtils

import scala.util.control.Exception._

object StringUtil {

  private lazy val BlowfishKey = {
    UUID.randomUUID().toString.substring(0, 16)
  }

  def base64Encode(value: Array[Byte]): String = {
    Base64.getEncoder.encodeToString(value)
  }

  def base64Decode(value: String): Array[Byte] = {
    Base64.getDecoder.decode(value)
  }

  def pbkdf2_sha256(iter: Int, salt: String, value: String): String = {
    val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val ks = new PBEKeySpec(value.toCharArray, base64Decode(salt), iter, 256)
    val s = keyFactory.generateSecret(ks)
    base64Encode(s.getEncoded)
  }

  def pbkdf2_sha256(value: String) = {
    val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val secureRandom = new SecureRandom
    val salt: Array[Byte] = new Array(32)
    secureRandom.nextBytes(salt)
    val iter = 100000
    val ks = new PBEKeySpec(value.toCharArray, salt, iter, 256)
    val s = keyFactory.generateSecret(ks)
    s"""$$pbkdf2-sha256$$${iter}$$${base64Encode(salt)}$$${base64Encode(s.getEncoded)}"""
  }

  def sha1(value: String): String =
    defining(java.security.MessageDigest.getInstance("SHA-1")) { md =>
      md.update(value.getBytes)
      md.digest.map(b => "%02x".format(b)).mkString
    }

  def md5(value: String): String = {
    val md = java.security.MessageDigest.getInstance("MD5")
    md.update(value.getBytes)
    md.digest.map(b => "%02x".format(b)).mkString
  }

  def encodeBlowfish(value: String): String = {
    val spec = new javax.crypto.spec.SecretKeySpec(BlowfishKey.getBytes(), "Blowfish")
    val cipher = javax.crypto.Cipher.getInstance("Blowfish")
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, spec)
    Base64.getEncoder.encodeToString(cipher.doFinal(value.getBytes("UTF-8")))
  }

  def decodeBlowfish(value: String): String = {
    val spec = new javax.crypto.spec.SecretKeySpec(BlowfishKey.getBytes(), "Blowfish")
    val cipher = javax.crypto.Cipher.getInstance("Blowfish")
    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec)
    new String(cipher.doFinal(Base64.getDecoder.decode(value)), "UTF-8")
  }

  def urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

  def urlDecode(value: String): String = URLDecoder.decode(value, "UTF-8")

  def splitWords(value: String): Array[String] = value.split("[ \\t　]+")

  def isInteger(value: String): Boolean = allCatch opt { value.toInt } map (_ => true) getOrElse (false)

  def escapeHtml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  /**
   * Make string from byte array. Character encoding is detected automatically by [[StringUtil.detectEncoding]].
   * And if given bytes contains UTF-8 BOM, it's removed from returned string.
   */
  def convertFromByteArray(content: Array[Byte]): String =
    IOUtils.toString(new BOMInputStream(new java.io.ByteArrayInputStream(content)), detectEncoding(content))

  def detectEncoding(content: Array[Byte]): String =
    defining(new UniversalDetector(null)) { detector =>
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
    if (lineSeparator == "CRLF") {
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
    if (lineSeparator == "CRLF") {
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
  def extractIssueId(message: String): Seq[String] =
    "(^|\\W)#(\\d+)(\\W|$)".r
      .findAllIn(message)
      .matchData
      .map(_.group(2))
      .toSeq
      .distinct

  /**
   * Extract close issue id like ```close #issueId ``` from the given message.
   *
   * @param message the message which may contains close command
   * @return the iterator of issue id
   */
  def extractCloseId(message: String): Seq[String] =
    "#(\\d+)".r
      .findAllIn(
        "(?i)(?<!\\w)(?:fix(?:e[sd])?|resolve[sd]?|close[sd]?)\\s+#(\\d+)(,\\s?#(\\d+))*(?!\\w)".r
          .findAllIn(message)
          .toSeq
          .mkString(",")
      )
      .matchData
      .map(_.group(1))
      .toSeq
      .distinct

  private val GitBucketUrlPattern = "^(https?://.+)/git/(.+?)/(.+?)\\.git$".r
  private val GitHubUrlPattern = "^https://(.+@)?github\\.com/(.+?)/(.+?)\\.git$".r
  private val BitBucketUrlPattern = "^https?://(.+@)?bitbucket\\.org/(.+?)/(.+?)\\.git$".r
  private val GitLabUrlPattern = "^https?://(.+@)?gitlab\\.com/(.+?)/(.+?)\\.git$".r

  def getRepositoryViewerUrl(gitRepositoryUrl: String, baseUrl: Option[String]): String = {
    def removeUserName(baseUrl: String): String = baseUrl.replaceFirst("(https?://).+@", "$1")

    gitRepositoryUrl match {
      case GitBucketUrlPattern(base, user, repository)
          if baseUrl.map(removeUserName(base).startsWith).getOrElse(false) =>
        s"${removeUserName(base)}/$user/$repository"
      case GitHubUrlPattern(_, user, repository)    => s"https://github.com/$user/$repository"
      case BitBucketUrlPattern(_, user, repository) => s"https://bitbucket.org/$user/$repository"
      case GitLabUrlPattern(_, user, repository)    => s"https://gitlab.com/$user/$repository"
      case _                                        => gitRepositoryUrl
    }
  }

  def cutTail(txt: String, limit: Int, suffix: String = ""): String = {
    txt.length match {
      case x if x > limit => txt.substring(0, limit).concat(suffix)
      case _              => txt
    }
  }

}
