package gitbucket.core.servlet

import java.io.{File, FileInputStream, FileOutputStream}
import java.text.MessageFormat
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import gitbucket.core.util.{FileUtil, StringUtil}
import org.apache.commons.io.{FileUtils, IOUtils}
import org.json4s.Formats
import org.json4s.jackson.Serialization._
import org.apache.http.HttpStatus

import scala.util.{Try, Using}

/**
 * Provides GitLFS Transfer API
 * https://github.com/git-lfs/git-lfs/blob/master/docs/api/basic-transfers.md
 */
class GitLfsTransferServlet extends HttpServlet {

  private implicit val jsonFormats: Formats = gitbucket.core.api.JsonFormat.jsonFormats
  private val LongObjectIdLength = 32
  private val LongObjectIdStringLength = LongObjectIdLength * 2

  override protected def doGet(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    for {
      (owner, repository, oid) <- getPathInfo(req, res) if checkToken(req, owner, repository, "download", oid, res)
    } yield {
      val file = new File(FileUtil.getLfsFilePath(owner, repository, oid))
      if (file.exists()) {
        res.setStatus(HttpStatus.SC_OK)
        res.setContentType("application/octet-stream")
        res.setHeader("Content-Length", file.length.toString)
        Using.resources(new FileInputStream(file), res.getOutputStream) { (in, out) =>
          IOUtils.copy(in, out)
          out.flush()
        }
      } else {
        sendError(res, HttpStatus.SC_NOT_FOUND, MessageFormat.format("Object ''{0}'' not found", oid))
      }
    }
  }

  override protected def doPut(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    for {
      (owner, repository, oid) <- getPathInfo(req, res) if checkToken(req, owner, repository, "upload", oid, res)
    } yield {
      val file = new File(FileUtil.getLfsFilePath(owner, repository, oid))
      FileUtils.forceMkdir(file.getParentFile)
      Using.resources(req.getInputStream, new FileOutputStream(file)) { (in, out) =>
        IOUtils.copy(in, out)
      }
      res.setStatus(HttpStatus.SC_OK)
    }
  }

  /** The token from the batch API is valid for one object, repository and operation until it expires. */
  private def checkToken(
    req: HttpServletRequest,
    owner: String,
    repository: String,
    operation: String,
    oid: String,
    res: HttpServletResponse
  ): Boolean = {
    val valid = Option(req.getHeader("Authorization"))
      .flatMap(token => Try(StringUtil.decodeBlowfish(token).split(" ")).toOption)
      .exists {
        case Array(expireAt, target, targetOperation, targetOid) =>
          target == s"$owner/$repository" && targetOperation == operation && targetOid == oid &&
          expireAt.toLong > System.currentTimeMillis
        case _ => false
      }
    if (!valid) sendError(res, HttpStatus.SC_UNAUTHORIZED, "Invalid or expired token")
    valid
  }

  private def getPathInfo(req: HttpServletRequest, res: HttpServletResponse): Option[(String, String, String)] = {
    req.getRequestURI.substring(1).split("/").reverse match {
      case Array(oid, repository, owner, _*) => Some((owner, repository, oid))
      case _                                 => None
    }
  }

  private def sendError(res: HttpServletResponse, status: Int, message: String): Unit = {
    res.setStatus(status)
    Using.resource(res.getWriter()) { out =>
      out.write(write(GitLfs.Error(message)))
      out.flush()
    }
  }

}
