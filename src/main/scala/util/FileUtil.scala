package util

import org.apache.commons.io.FileUtils
import java.net.URLConnection
import java.io.File
import util.ControlUtil._
import scala.util.Random
import eu.medsea.mimeutil.{MimeUtil2, MimeType}

object FileUtil {

  private val mimeUtil = new MimeUtil2()
  mimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.MagicMimeMimeDetector")

  def getMimeType(name: String): String =
    defining(URLConnection.getFileNameMap()){ fileNameMap =>
      fileNameMap.getContentTypeFor(name) match {
        case null     => "application/octet-stream"
        case mimeType => mimeType
      }
    }

  /**
   * Returns mime type detected by file content.
   *
   * @param file File object
   * @return mime type String
   */
  def getMimeType(file: File): String = {
    MimeUtil2.getMostSpecificMimeType(mimeUtil.getMimeTypes(file, new MimeType("application/octet-stream"))).toString
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
}
