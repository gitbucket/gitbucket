package util

import java.text.SimpleDateFormat
import javax.servlet.http.HttpSession
import util.Directory._
import org.apache.commons.io.FileUtils

object FileUploadUtil {

  def generateFileId: String =
    new SimpleDateFormat("yyyyMMddHHmmSSsss").format(new java.util.Date(System.currentTimeMillis))

  def TemporaryDir(implicit session: HttpSession): java.io.File =
    new java.io.File(GitBucketHome, "tmp/_upload/%s".format(session.getId))

  def getTemporaryFile(fileId: String)(implicit session: HttpSession): java.io.File =
    new java.io.File(TemporaryDir, fileId)

  def removeTemporaryFile(fileId: String)(implicit session: HttpSession): Unit =
    getTemporaryFile(fileId).delete()

  def removeTemporaryFiles()(implicit session: HttpSession): Unit =
    FileUtils.deleteDirectory(TemporaryDir)

  def getUploadedFilename(fileId: String)(implicit session: HttpSession): Option[String] = {
    val filename = Option(session.getAttribute("upload_" + fileId).asInstanceOf[String])
    if(filename.isDefined){
      session.removeAttribute("upload_" + fileId)
    }
    filename
  }

}
