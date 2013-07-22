package app

import service._
import util.Directory._
import util.{UsersAuthenticator, OwnerAuthenticator}
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.scalatra.FlashMapSupport

class RepositorySettingsController extends RepositorySettingsControllerBase
  with RepositoryService with AccountService with OwnerAuthenticator with UsersAuthenticator

trait RepositorySettingsControllerBase extends ControllerBase with FlashMapSupport {
  self: RepositoryService with AccountService with OwnerAuthenticator with UsersAuthenticator =>

  case class OptionsForm(description: Option[String], defaultBranch: String, isPrivate: Boolean)
  
  val optionsForm = mapping(
    "description"   -> trim(label("Description"    , optional(text()))),
    "defaultBranch" -> trim(label("Default Branch" , text(required, maxlength(100)))),
    "isPrivate"     -> trim(label("Repository Type", boolean()))
  )(OptionsForm.apply)
  
  case class CollaboratorForm(userName: String)

  val collaboratorForm = mapping(
    "userName" -> trim(label("Username", text(required, collaborator)))
  )(CollaboratorForm.apply)

  /**
   * Redirect to the Options page.
   */
  get("/:owner/:repository/settings")(ownerOnly { repository =>
    redirect(s"/${repository.owner}/${repository.name}/settings/options")
  })
  
  /**
   * Display the Options page.
   */
  get("/:owner/:repository/settings/options")(ownerOnly {
    settings.html.options(_, flash.get("info"))
  })
  
  /**
   * Save the repository options.
   */
  post("/:owner/:repository/settings/options", optionsForm)(ownerOnly { (form, repository) =>
    saveRepositoryOptions(repository.owner, repository.name, form.description, form.defaultBranch, form.isPrivate)
    flash += "info" -> "Repository settings has been updated."
    redirect(s"/${repository.owner}/${repository.name}/settings/options")
  })
  
  /**
   * Display the Collaborators page.
   */
  get("/:owner/:repository/settings/collaborators")(ownerOnly { repository =>
    settings.html.collaborators(getCollaborators(repository.owner, repository.name), repository)
  })

  /**
   * JSON API for collaborator completion.
   */
  // TODO Merge with UserManagementController
  get("/:owner/:repository/settings/collaborators/proposals")(usersOnly {
    contentType = formats("json")
    org.json4s.jackson.Serialization.write(Map("options" -> getAllUsers.filter(!_.isGroupAccount).map(_.userName).toArray))
  })

  /**
   * Add the collaborator.
   */
  post("/:owner/:repository/settings/collaborators/add", collaboratorForm)(ownerOnly { (form, repository) =>
    addCollaborator(repository.owner, repository.name, form.userName)
    redirect(s"/${repository.owner}/${repository.name}/settings/collaborators")
  })

  /**
   * Add the collaborator.
   */
  get("/:owner/:repository/settings/collaborators/remove")(ownerOnly { repository =>
    removeCollaborator(repository.owner, repository.name, params("name"))
    redirect(s"/${repository.owner}/${repository.name}/settings/collaborators")
  })

  /**
   * Display the delete repository page.
   */
  get("/:owner/:repository/settings/delete")(ownerOnly {
    settings.html.delete(_)
  })

  /**
   * Delete the repository.
   */
  post("/:owner/:repository/settings/delete")(ownerOnly { repository =>
    deleteRepository(repository.owner, repository.name)

    FileUtils.deleteDirectory(getRepositoryDir(repository.owner, repository.name))
    FileUtils.deleteDirectory(getWikiRepositoryDir(repository.owner, repository.name))
    FileUtils.deleteDirectory(getTemporaryDir(repository.owner, repository.name))

    redirect(s"/${repository.owner}")
  })

  /**
   * Provides Constraint to validate the collaborator name.
   */
  private def collaborator: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      val paths = request.getRequestURI.split("/")
      getAccountByUserName(value) match {
        case None => Some("User does not exist.")
        case Some(x) if(x.userName == paths(1) || getCollaborators(paths(1), paths(2)).contains(x.userName))
                  => Some("User can access this repository already.")
        case _    => None
      }
    }
  }

}