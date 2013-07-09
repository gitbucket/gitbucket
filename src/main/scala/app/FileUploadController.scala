package app

import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}

/**
 * Provides Ajax based file upload functionality.
 *
 * This servlet saves uploaded file as temporary file and returns the unique key.
 * You can get uploaded file using [[app.FileUploadUtil#getFile()]] with this key.
 */
// TODO Remove temporary files at session timeout by session listener.
class FileUploadController extends ScalatraServlet with FileUploadSupport with FlashMapSupport {
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3*1024*1024)))

  post("/"){
    fileParams.get("file") match {
      // TODO save as temporary file and return key.
      case Some(file) => {
        println(file.name)
        println(file.size)
        Ok("1234")
      }
      case None => BadRequest
    }
  }

}

// TODO Not implemented yet.
object FileUploadUtil {
  def getFile(key: String) = {

  }
}

