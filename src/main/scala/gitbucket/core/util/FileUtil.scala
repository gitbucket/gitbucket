package gitbucket.core.util

import org.apache.commons.io.FileUtils
import org.apache.tika.Tika
import java.io.File
import ControlUtil._
import scala.util.Random

object FileUtil {

  def getMimeType(name: String): String =
    defining(new Tika()){ tika =>
      tika.detect(name) match {
        case null     => "application/octet-stream"
        case mimeType => mimeType
      }
    }

  def getContentType(name: String, bytes: Array[Byte]): String = {
    defining(getMimeType(name)){ mimeType =>
      if(mimeType == "application/octet-stream" && isText(bytes)){
        "text/plain"
      } else {
        mimeType
      }
    }
  }

  def isImage(name: String): Boolean = getMimeType(name).startsWith("image/")

  def isUploadableType(name: String): Boolean = mimeTypeWhiteList contains getMimeType(name)

  def acceptAllMimeType(name: String): Boolean = true

  def isLarge(size: Long): Boolean = (size > 1024 * 1000)

  def isText(content: Array[Byte]): Boolean = !content.contains(0)

  def generateFileId: String = System.currentTimeMillis + Random.alphanumeric.take(10).mkString

  def getExtension(name: String): String =
    name.lastIndexOf('.') match {
      case i if(i >= 0) => name.substring(i + 1)
      case _ => ""
    }

  def withTmpDir[A](dir: File)(action: File => A): A = {
    if(dir.exists()){
      FileUtils.deleteDirectory(dir)
    }
    try {
      action(dir)
    } finally {
      FileUtils.deleteDirectory(dir)
    }
  }

  val mimeTypeWhiteList: Array[String] = Array(
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "image/gif",
      "image/jpeg",
      "image/png",
      "text/plain",
      "application/zip")
}
