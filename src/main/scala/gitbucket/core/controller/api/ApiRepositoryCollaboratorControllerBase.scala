package gitbucket.core.controller.api
import gitbucket.core.api.{AddACollaborator, ApiRepositoryCollaborator, ApiUser, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{OwnerAuthenticator, ReferrerAuthenticator}
import org.scalatra.NoContent

trait ApiRepositoryCollaboratorControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with ReferrerAuthenticator with OwnerAuthenticator =>

  /*
   * i. List repository collaborators
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#list-repository-collaborators
   */
  get("/api/v3/repos/:owner/:repository/collaborators")(referrersOnly { repository =>
    // TODO Should ApiUser take permission? getCollaboratorUserNames does not return owner group members.
    JsonFormat(
      getCollaboratorUserNames(params("owner"), params("repository")).map(u => ApiUser(getAccountByUserName(u).get))
    )
  })

  /*
   * ii. Check if a user is a collaborator
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#check-if-a-user-is-a-repository-collaborator
   */
  get("/api/v3/repos/:owner/:repository/collaborators/:userName")(referrersOnly { repository =>
    (for (account <- getAccountByUserName(params("userName"))) yield {
      if (getCollaboratorUserNames(repository.owner, repository.name).contains(account.userName)) {
        NoContent()
      } else {
        NotFound()
      }
    }) getOrElse NotFound()
  })

  /*
   * iii. Get repository permissions for a user
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#get-repository-permissions-for-a-user
   */
  get("/api/v3/repos/:owner/:repository/collaborators/:userName/permission")(referrersOnly { repository =>
    (for {
      account <- getAccountByUserName(params("userName"))
      collaborator <- getCollaborators(repository.owner, repository.name)
        .find(p => p._1.collaboratorName == account.userName)
    } yield {
      JsonFormat(
        ApiRepositoryCollaborator(collaborator._1.role, ApiUser(account))
      )
    }) getOrElse NotFound()
  })

  /*
   * iv. Add a repository collaborator
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#add-a-repository-collaborator
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
   * v. Remove a repository collaborator
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#remove-a-repository-collaborator
   * requested #1586
   */
  delete("/api/v3/repos/:owner/:repository/collaborators/:userName")(ownerOnly { repository =>
    removeCollaborator(repository.owner, repository.name, params("userName"))
    NoContent()
  })
}
