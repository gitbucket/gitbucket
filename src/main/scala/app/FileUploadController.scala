package app

import util.Directory._
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import java.text.SimpleDateFormat
import org.apache.commons.io.FileUtils
import javax.servlet.http.HttpSession

/**
 * Provides Ajax based file upload functionality.
 *
 * This servlet saves uploaded file as temporary file and returns the unique key.
 * You can get uploaded file using [[app.FileUploadUtil#getFile()]] with this key.
 */
// TODO Remove temporary files at session timeout by session listener.
class FileUploadController extends ScalatraServlet with FileUploadSupport with FlashMapSupport {
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3 * 1024 * 1024)))

  post("/"){
    fileParams.get("file") match {
      case Some(file) => {
        val fileId  = new SimpleDateFormat("yyyyMMddHHmmSSsss").format(new java.util.Date(System.currentTimeMillis))
        FileUtils.writeByteArrayToFile(FileUploadUtil.getTemporaryFile(fileId), file.get)
        session += "fileId" -> file.name
        Ok(fileId)
      }
      case None => BadRequest
    }
  }

}

// TODO move to other package or should be trait?
object FileUploadUtil {

  def TemporaryDir(implicit session: HttpSession): java.io.File =
    new java.io.File(GitBucketHome, "tmp/_%s".format(session.getId))

  def getTemporaryFile(fileId: String)(implicit session: HttpSession): java.io.File =
    new java.io.File(TemporaryDir, fileId)

  def removeTemporaryFile(fileId: String)(implicit session: HttpSession): Unit =
    getTemporaryFile(fileId).delete()

  def removeTemporaryFiles()(implicit session: HttpSession): Unit =
    FileUtils.deleteDirectory(TemporaryDir)

  def getFilename(fileId: String)(implicit session: HttpSession): Option[String] = {
    val filename = Option(session.getAttribute(fileId).asInstanceOf[String])
    if(filename.isDefined){
      session.removeAttribute(fileId)
    }
    filename
  }

}

