package util

import java.net.{URLDecoder, URLEncoder}

object StringUtil {

  def sha1(value: String): String = {
    val md = java.security.MessageDigest.getInstance("SHA-1")
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

}
