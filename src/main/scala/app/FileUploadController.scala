package app

import util.{FileUtil}
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import org.apache.commons.io.FileUtils

/**
 * Provides Ajax based file upload functionality.
 *
 * This servlet saves uploaded file as temporary file and returns the unique id.
 * You can get uploaded file using [[app.FileUploadControllerBase#getTemporaryFile()]] with this id.
 */
class FileUploadController extends ScalatraServlet
  with FileUploadSupport with FlashMapSupport with FileUploadControllerBase {

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3 * 1024 * 1024)))

  post("/image"){
    fileParams.get("file") match {
      case Some(file) if(FileUtil.isImage(file.name)) => {
        val fileId  = generateFileId
        FileUtils.writeByteArrayToFile(getTemporaryFile(fileId), file.get)
        session += "upload_" + fileId -> file.name
        Ok(fileId)
      }
      case None => BadRequest
    }
  }

}

