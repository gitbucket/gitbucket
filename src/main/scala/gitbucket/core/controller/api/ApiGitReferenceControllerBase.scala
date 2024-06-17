package gitbucket.core.controller.api
import gitbucket.core.api.{ApiError, ApiRef, CreateARef, JsonFormat, UpdateARef}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{ReferrerAuthenticator, RepositoryName, WritableUsersAuthenticator}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RefUpdate.Result
import org.scalatra.{BadRequest, NoContent, UnprocessableEntity}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.util.Using

trait ApiGitReferenceControllerBase extends ControllerBase {
  self: ReferrerAuthenticator & WritableUsersAuthenticator =>

  private val logger = LoggerFactory.getLogger(classOf[ApiGitReferenceControllerBase])

  get("/api/v3/repos/:owner/:repository/git/refs")(referrersOnly { repository =>
    val result = Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val refs = git
        .getRepository()
        .getRefDatabase()
        .getRefsByPrefix("refs")
        .asScala

      refs.map(ApiRef.fromRef(RepositoryName(s"${repository.owner}/${repository.name}"), _))
    }
    JsonFormat(result)
  })

  /*
   * i. Get a reference
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#get-a-reference
   */
  get("/api/v3/repos/:owner/:repository/git/ref/*")(referrersOnly { repository =>
    val revstr = multiParams("splat").head
    getRef(revstr, repository)
  })

  // Some versions of GHE support this path
  get("/api/v3/repos/:owner/:repository/git/refs/*")(referrersOnly { repository =>
    logger.warn("git/refs/ endpoint may not be compatible with GitHub API v3. Consider using git/ref/ endpoint instead")
    val revstr = multiParams("splat").head
    getRef(revstr, repository)
  })

  /*
   * ii. Get all references
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#list-matching-references
   */

  /*
   * iii. Create a reference
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#create-a-reference
   */
  post("/api/v3/repos/:owner/:repository/git/refs")(writableUsersOnly { repository =>
    extractFromJsonBody[CreateARef].map { data =>
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val ref = git.getRepository.findRef(data.ref)
        if (ref == null) {
          val update = git.getRepository.updateRef(data.ref)
          update.setNewObjectId(ObjectId.fromString(data.sha))
          val result = update.update()
          result match {
            case Result.NEW =>
              JsonFormat(
                ApiRef
                  .fromRef(RepositoryName(repository.owner, repository.name), git.getRepository.findRef(data.ref))
              )
            case _ => UnprocessableEntity(result.name())
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
  patch("/api/v3/repos/:owner/:repository/git/refs/*")(writableUsersOnly { repository =>
    val refName = multiParams("splat").mkString("/")
    extractFromJsonBody[UpdateARef].map { data =>
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
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
              JsonFormat(ApiRef.fromRef(RepositoryName(repository), git.getRepository.findRef(refName)))
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
  delete("/api/v3/repos/:owner/:repository/git/refs/*")(writableUsersOnly { _ =>
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

  private def notFound(): ApiError = {
    response.setStatus(404)
    ApiError("Not Found")
  }

  protected def getRef(revstr: String, repository: RepositoryInfo): AnyRef = {
    logger.debug(s"getRef: path '${revstr}'")

    val name = RepositoryName(repository)
    val result = JsonFormat(revstr match {
      case "tags" => repository.tags.map(ApiRef.fromTag(name, _))
      case x if x.startsWith("tags/") =>
        val tagName = x.substring("tags/".length)
        repository.tags.find(_.name == tagName) match {
          case Some(tagInfo) => ApiRef.fromTag(name, tagInfo)
          case None          => notFound()
        }
      case other =>
        Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
          git.getRepository().findRef(other) match {
            case null => notFound()
            case ref  => ApiRef.fromRef(name, ref)
          }
        }
    })

    logger.debug(s"json result: $result")
    result
  }
}
