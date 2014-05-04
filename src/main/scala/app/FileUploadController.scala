package app

import util.{Keys, FileUtil}
import util.ControlUtil._
import util.Directory._
import org.scalatra._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport, FileItem}
import org.apache.commons.io.FileUtils

/**
 * Provides Ajax based file upload functionality.
 *
 * This servlet saves uploaded file.
 */
class FileUploadController extends ScalatraServlet with FileUploadSupport {

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3 * 1024 * 1024)))

  post("/image"){
    execute { (file, fileId) =>
      FileUtils.writeByteArrayToFile(new java.io.File(getTemporaryDir(session.getId), fileId), file.get)
      session += Keys.Session.Upload(fileId) -> file.name
    }
  }

  post("/image/:owner/:repository"){
    execute { (file, fileId) =>
      FileUtils.writeByteArrayToFile(new java.io.File(
        getAttachedDir(params("owner"), params("repository")),
        fileId + "." + FileUtil.getExtension(file.getName)), file.get)
    }
  }

  private def execute(f: (FileItem, String) => Unit) = fileParams.get("file") match {
    case Some(file) if(FileUtil.isImage(file.name)) =>
      defining(FileUtil.generateFileId){ fileId =>
        f(file, fileId)

        Ok(fileId)
      }
    case _ => BadRequest
  }

}
