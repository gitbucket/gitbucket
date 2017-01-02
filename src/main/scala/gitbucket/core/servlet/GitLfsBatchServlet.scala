package gitbucket.core.servlet

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import org.json4s._
import org.json4s.jackson.Serialization.{read, write}

import java.util.Date

/**
 * Provides GitLFS Batch API.
 *
 * https://github.com/git-lfs/git-lfs/blob/master/docs/api/batch.md
 */
class GitLfsBatchServlet extends HttpServlet {

  // TODO GitLFS server url must be configurable
  private val GitLfsServerUrl = "http://localhost:9090/git-lfs"
  
  private implicit val jsonFormats = gitbucket.core.api.JsonFormat.jsonFormats

  override protected def doPost(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    val batchRequest = read[BatchRequest](req.getInputStream)

    val batchResponse = batchRequest.operation match {
      case "upload" =>
        BatchUploadResponse("basic", batchRequest.objects.map { requestObject =>
          BatchResponseObject(
            requestObject.oid,
            requestObject.size,
            true,
            Actions(
              upload = Some(Action(
                href = GitLfsServerUrl + "/" + requestObject.oid,
                expires_at = new Date(System.currentTimeMillis + 60000)
              ))
            )
          )
        })
      case "download" =>
        BatchUploadResponse("basic", batchRequest.objects.map { requestObject =>
          BatchResponseObject(
            requestObject.oid,
            requestObject.size,
            true,
            Actions(
              download = Some(Action(
                href = GitLfsServerUrl + "/" + requestObject.oid,
                expires_at = new Date(System.currentTimeMillis + 60000)
              ))
            )
          )
        })
    }

    res.setContentType("application/vnd.git-lfs+json")

    val out = res.getWriter
    out.print(write(batchResponse))
    out.flush()
  }

}

case class BatchRequest(
  operation: String,
  transfers: Seq[String],
  objects: Seq[BatchRequestObject]
)

case class BatchRequestObject(
  oid: String,
  size: Long
)

case class BatchUploadResponse(
  transfer: String,
  objects: Seq[BatchResponseObject]
)

case class BatchResponseObject(
  oid: String,
  size: Long,
  authenticated: Boolean,
  actions: Actions
)

case class Actions(
  download: Option[Action] = None,
  upload: Option[Action] = None
)

case class Action(
  href: String,
  header: Map[String, String] = Map.empty,
  expires_at: Date
)