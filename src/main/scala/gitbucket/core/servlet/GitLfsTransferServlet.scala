package gitbucket.core.servlet

import java.io.{File, FileInputStream, FileOutputStream}
import java.text.MessageFormat
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import gitbucket.core.util.Directory
import org.apache.commons.io.{FileUtils, IOUtils}
import org.json4s.jackson.Serialization._
import org.apache.http.HttpStatus
import gitbucket.core.util.ControlUtil._

/**
 * Provides GitLFS Transfer API
 * https://github.com/git-lfs/git-lfs/blob/master/docs/api/basic-transfers.md
 */
class GitLfsTransferServlet extends HttpServlet {

  private implicit val jsonFormats = gitbucket.core.api.JsonFormat.jsonFormats
  private val LongObjectIdLength = 32
  private val LongObjectIdStringLength = LongObjectIdLength * 2

  override protected def doGet(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    for {
      oid <- getObjectId(req, res)
    } yield {
      val file = new File(Directory.LfsHome + "/" + oid.substring(0, 2) + "/" + oid.substring(2, 4) + "/" + oid)
      if(file.exists()){
        res.setStatus(HttpStatus.SC_OK)
        res.setContentType("application/octet-stream")
        res.setContentLength(file.length.toInt)
        using(new FileInputStream(file), res.getOutputStream){ (in, out) =>
          IOUtils.copy(in, out)
          out.flush()
        }
      } else {
        sendError(res, HttpStatus.SC_NOT_FOUND,
          MessageFormat.format("Object ''{0}'' not found", oid))
      }
    }
  }

  override protected def doPut(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    for {
      oid <- getObjectId(req, res)
    } yield {
      val file = new File(Directory.LfsHome + "/" + oid.substring(0, 2) + "/" + oid.substring(2, 4) + "/" + oid)
      FileUtils.forceMkdir(file.getParentFile)
      using(req.getInputStream, new FileOutputStream(file)){ (in, out) =>
        IOUtils.copy(in, out)
      }
      res.setStatus(HttpStatus.SC_OK)
    }
  }

  private def getObjectId(req: HttpServletRequest, rsp: HttpServletResponse): Option[String] = {
    val info: String = req.getPathInfo
    val length: Int = 1 + LongObjectIdStringLength
    if (info.length != length) {
      sendError(rsp, HttpStatus.SC_UNPROCESSABLE_ENTITY,
        MessageFormat.format("Invalid pathInfo ''{0}'' does not match ''/'{'SHA-256'}'''", info))
      None
    } else {
      Some(info.substring(1, length))
    }
  }

  private def sendError(res: HttpServletResponse, status: Int, message: String): Unit = {
    res.setStatus(status)
    using(res.getWriter()){ out =>
      out.write(write(GitLfs.Error(message)))
      out.flush()
    }
  }

}


