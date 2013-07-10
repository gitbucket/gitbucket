package util

object StringUtil {

  def encrypt(value: String): String = {
    val md = java.security.MessageDigest.getInstance("SHA-1")
    md.update(value.getBytes)
    md.digest.map(b => "%02x".format(b)).mkString
  }

  def md5(value: String): String = {
    val md = java.security.MessageDigest.getInstance("MD5")
    md.update(value.getBytes)
    md.digest.map(b => "%02x".format(b)).mkString
  }

}
