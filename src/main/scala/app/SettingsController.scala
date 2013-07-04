package app

import service._
import util.Directory._
import util.{UsersAuthenticator, OwnerAuthenticator}
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils

class SettingsController extends SettingsControllerBase
  with RepositoryService with AccountService with OwnerAuthenticator with UsersAuthenticator

trait SettingsControllerBase extends ControllerBase {
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
    redirect("/%s/%s/settings/options".format(repository.owner, repository.name))
  })
  
  /**
   * Display the Options page.
   */
  get("/:owner/:repository/settings/options")(ownerOnly {
    settings.html.options(_)
  })
  
  /**
   * Save the repository options.
   */
  post("/:owner/:repository/settings/options", optionsForm)(ownerOnly { (form, repository) =>
    saveRepositoryOptions(repository.owner, repository.name, form.description, form.defaultBranch, form.isPrivate)
    redirect("/%s/%s/settings/options".format(repository.owner, repository.name))
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
  get("/:owner/:repository/settings/collaborators/proposals")(usersOnly {
    contentType = formats("json")
    org.json4s.jackson.Serialization.write(Map("options" -> getAllUsers.map(_.userName).toArray))
  })

  /**
   * Add the collaborator.
   */
  post("/:owner/:repository/settings/collaborators/add", collaboratorForm)(ownerOnly { (form, repository) =>
    addCollaborator(repository.owner, repository.name, form.userName)
    redirect("/%s/%s/settings/collaborators".format(repository.owner, repository.name))
  })

  /**
   * Add the collaborator.
   */
  get("/:owner/:repository/settings/collaborators/remove")(ownerOnly { repository =>
    removeCollaborator(repository.owner, repository.name, params("name"))
    redirect("/%s/%s/settings/collaborators".format(repository.owner, repository.name))
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

    redirect("/%s".format(repository.owner))
  })

  /**
   * Provides Constraint to validate the collaborator name.
   */
  private def collaborator: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      getAccountByUserName(value) match {
        case None => Some("User does not exist.")
        case Some(x) if(x.userName == context.loginAccount.get.userName) => Some("User can access this repository already.")
        case Some(x) => {
          val paths = request.getRequestURI.split("/")
          if(getCollaborators(paths(1), paths(2)).contains(x.userName)){
            Some("User can access this repository already.")
          } else {
            None
          }
        }
      }
    }
  }

}