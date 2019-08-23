package gitbucket.core.util

import org.apache.commons.io.FileUtils
import org.apache.tika.Tika
import java.io.File
import SyntaxSugars._
import scala.util.Random

object FileUtil {

  def getMimeType(name: String): String =
    defining(new Tika()) { tika =>
      tika.detect(name) match {
        case null     => "application/octet-stream"
        case mimeType => mimeType
      }
    }

  def getMimeType(name: String, bytes: Array[Byte]): String = {
    defining(getMimeType(name)) { mimeType =>
      if (mimeType == "application/octet-stream" && isText(bytes)) {
        "text/plain"
      } else {
        mimeType
      }
    }
  }

  def getSafeMimeType(name: String): String = {
    getMimeType(name).replace("text/html", "text/plain")
  }

  def isImage(name: String): Boolean = getMimeType(name).startsWith("image/")

  def isLarge(size: Long): Boolean = (size > 1024 * 1000)

  def isText(content: Array[Byte]): Boolean = !content.contains(0)

  def generateFileId: String = s"${System.currentTimeMillis}${Random.alphanumeric.take(10).mkString}"

  def getExtension(name: String): String =
    name.lastIndexOf('.') match {
      case i if (i >= 0) => name.substring(i + 1)
      case _             => ""
    }

  def withTmpDir[A](dir: File)(action: File => A): A = {
    if (dir.exists()) {
      FileUtils.deleteDirectory(dir)
    }
    try {
      action(dir)
    } finally {
      FileUtils.deleteDirectory(dir)
    }
  }

  def getLfsFilePath(owner: String, repository: String, oid: String): String =
    s"${Directory.getLfsDir(owner, repository)}/${checkFilename(oid)}"

  def readableSize(size: Long): String = FileUtils.byteCountToDisplaySize(size)

  /**
   * Delete the given directory if it's empty.
   * Do nothing if the given File is not a directory or not empty.
   */
  def deleteDirectoryIfEmpty(dir: File): Unit = {
    if (dir.isDirectory() && dir.list().isEmpty) {
      FileUtils.deleteDirectory(dir)
    }
  }

  /**
   * Delete file or directory forcibly.
   */
  def deleteIfExists(file: java.io.File): java.io.File = {
    if (file.exists) {
      FileUtils.forceDelete(file)
    }
    file
  }

  /**
   * Create an instance of java.io.File safely.
   */
  def checkFilename(name: String): String = {
    if (name.contains("..")) {
      throw new IllegalArgumentException(s"Invalid file name: ${name}")
    }
    name
  }

  lazy val MaxFileSize =
    if (System.getProperty("gitbucket.maxFileSize") != null)
      System.getProperty("gitbucket.maxFileSize").toLong
    else
      3 * 1024 * 1024

  lazy val UploadTimeout =
    if (System.getProperty("gitbucket.UploadTimeout") != null)
      System.getProperty("gitbucket.UploadTimeout").toLong
    else
      3 * 10

  lazy val MaxFileSizeReleases =
    if (System.getProperty("gitbucket.maxFileSizeReleases") != null)
      System.getProperty("gitbucket.maxFileSizeReleases").toLong
    else
      MaxFileSize

  lazy val UploadTimeoutIssues =
    if (System.getProperty("gitbucket.UploadTimeoutIssues") != null)
      System.getProperty("gitbucket.UploadTimeoutIssues").toLong
    else
      UploadTimeout

  lazy val MaxFileSizeIssues =
    if (System.getProperty("gitbucket.maxFileSizeIssues") != null)
      System.getProperty("gitbucket.maxFileSizeIssues").toLong
    else
      MaxFileSize

  lazy val UploadTimeoutReleases =
    if (System.getProperty("gitbucket.UploadTimeoutReleases") != null)
      System.getProperty("gitbucket.UploadTimeoutReleases").toLong
    else
      UploadTimeout

}
