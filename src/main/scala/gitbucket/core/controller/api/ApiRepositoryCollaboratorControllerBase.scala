package gitbucket.core.controller.api
import gitbucket.core.api.{AddACollaborator, ApiUser, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{OwnerAuthenticator, ReferrerAuthenticator}
import org.scalatra.NoContent

trait ApiRepositoryCollaboratorControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with ReferrerAuthenticator with OwnerAuthenticator =>

  /*
   * i. List collaborators
   * https://developer.github.com/v3/repos/collaborators/#list-collaborators
   */
  get("/api/v3/repos/:owner/:repository/collaborators")(referrersOnly { repository =>
    // TODO Should ApiUser take permission? getCollaboratorUserNames does not return owner group members.
    JsonFormat(
      getCollaboratorUserNames(params("owner"), params("repository")).map(u => ApiUser(getAccountByUserName(u).get))
    )
  })
  /*
   * ii. Check if a user is a collaborator
   * https://developer.github.com/v3/repos/collaborators/#check-if-a-user-is-a-collaborator
   */

  /*
   * iii. Review a user's permission level
   * https://developer.github.com/v3/repos/collaborators/#review-a-users-permission-level
   */

  /*
   * iv. Add user as a collaborator
   * https://developer.github.com/v3/repos/collaborators/#add-user-as-a-collaborator
   * requested #1586
   */
  put("/api/v3/repos/:owner/:repository/collaborators/:userName")(ownerOnly { repository =>
    for {
      data <- extractFromJsonBody[AddACollaborator]
    } yield {
      addCollaborator(repository.owner, repository.name, params("userName"), data.role)
      NoContent()
    }
  })

  /*
   * v. Remove user as a collaborator
   * https://developer.github.com/v3/repos/collaborators/#remove-user-as-a-collaborator
   * requested #1586
   */
  delete("/api/v3/repos/:owner/:repository/collaborators/:userName")(ownerOnly { repository =>
    removeCollaborator(repository.owner, repository.name, params("userName"))
    NoContent()
  })
}
