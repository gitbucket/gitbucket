package gitbucket.core.controller.api
import gitbucket.core.api.{ApiObject, ApiRef, CreateARef, JsonFormat, UpdateARef}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.ReferrerAuthenticator
import gitbucket.core.util.Implicits._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RefUpdate.Result
import org.scalatra.{BadRequest, NoContent, UnprocessableEntity}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.util.Using

trait ApiGitReferenceControllerBase extends ControllerBase {
  self: ReferrerAuthenticator =>

  private val logger = LoggerFactory.getLogger(classOf[ApiGitReferenceControllerBase])

  /*
   * i. Get a reference
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#get-a-reference
   */
  get("/api/v3/repos/:owner/:repository/git/ref/*")(referrersOnly { repository =>
    getRef()
  })

  // Some versions of GHE support this path
  get("/api/v3/repos/:owner/:repository/git/refs/*")(referrersOnly { repository =>
    logger.warn("git/refs/ endpoint may not be compatible with GitHub API v3. Consider using git/ref/ endpoint instead")
    getRef()
  })

  private def getRef() = {
    val revstr = multiParams("splat").head
    Using.resource(Git.open(getRepositoryDir(params("owner"), params("repository")))) { git =>
      val ref = git.getRepository().findRef(revstr)

      if (ref != null) {
        val sha = ref.getObjectId().name()
        JsonFormat(ApiRef(revstr, ApiObject(sha)))

      } else {
        val refs = git
          .getRepository()
          .getRefDatabase()
          .getRefsByPrefix("refs/")
          .asScala

        JsonFormat(refs.map { ref =>
          val sha = ref.getObjectId().name()
          ApiRef(revstr, ApiObject(sha))
        })
      }
    }
  }

  /*
   * ii. Get all references
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#list-matching-references
   */

  /*
   * iii. Create a reference
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#create-a-reference
   */
  post("/api/v3/repos/:owner/:repository/git/refs")(referrersOnly { _ =>
    extractFromJsonBody[CreateARef].map {
      data =>
        Using.resource(Git.open(getRepositoryDir(params("owner"), params("repository")))) { git =>
          val ref = git.getRepository.findRef(data.ref)
          if (ref == null) {
            val update = git.getRepository.updateRef(data.ref)
            update.setNewObjectId(ObjectId.fromString(data.sha))
            val result = update.update()
            result match {
              case Result.NEW => JsonFormat(ApiRef(update.getName, ApiObject(update.getNewObjectId.getName)))
              case _          => UnprocessableEntity(result.name())
            }
          } else {
            UnprocessableEntity("Ref already exists.")
          }
        }
    } getOrElse BadRequest()
  })

  /*
   * iv. Update a reference
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#update-a-reference
   */
  patch("/api/v3/repos/:owner/:repository/git/refs/*")(referrersOnly { _ =>
    val refName = multiParams("splat").mkString("/")
    extractFromJsonBody[UpdateARef].map {
      data =>
        Using.resource(Git.open(getRepositoryDir(params("owner"), params("repository")))) { git =>
          val ref = git.getRepository.findRef(refName)
          if (ref == null) {
            UnprocessableEntity("Ref does not exist.")
          } else {
            val update = git.getRepository.updateRef(ref.getName)
            update.setNewObjectId(ObjectId.fromString(data.sha))
            update.setForceUpdate(data.force)
            val result = update.update()
            result match {
              case Result.FORCED | Result.FAST_FORWARD | Result.NO_CHANGE =>
                JsonFormat(ApiRef(update.getName, ApiObject(update.getNewObjectId.getName)))
              case _ => UnprocessableEntity(result.name())
            }
          }
        }
    } getOrElse BadRequest()
  })

  /*
   * v. Delete a reference
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#delete-a-reference
   */
  delete("/api/v3/repos/:owner/:repository/git/refs/*")(referrersOnly { _ =>
    val refName = multiParams("splat").mkString("/")
    Using.resource(Git.open(getRepositoryDir(params("owner"), params("repository")))) { git =>
      val ref = git.getRepository.findRef(refName)
      if (ref == null) {
        UnprocessableEntity("Ref does not exist.")
      } else {
        val update = git.getRepository.updateRef(ref.getName)
        update.setForceUpdate(true)
        val result = update.delete()
        result match {
          case Result.FORCED => NoContent()
          case _             => UnprocessableEntity(result.name())
        }
      }
    }
  })
}
