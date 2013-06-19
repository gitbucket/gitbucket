package app

import service._
import util.Directory._
import util.OwnerOnlyAuthenticator
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils

class SettingsController extends SettingsControllerBase
  with RepositoryService with AccountService with OwnerOnlyAuthenticator

trait SettingsControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with OwnerOnlyAuthenticator =>

  case class OptionsForm(description: Option[String], defaultBranch: String, repositoryType: Int)
  
  val optionsForm = mapping(
    "description"    -> trim(label("Description"    , optional(text()))),
    "defaultBranch"  -> trim(label("Default Branch" , text(required, maxlength(100)))),
    "repositoryType" -> trim(label("Repository Type", number()))
  )(OptionsForm.apply)
  
  case class CollaboratorForm(userName: String)

  val collaboratorForm = mapping(
    "userName" -> trim(label("Username", text(required, collaborator)))
  )(CollaboratorForm.apply)

  /**
   * Redirect to the Options page.
   */
  get("/:owner/:repository/settings")(ownerOnly {
    val owner      = params("owner")
    val repository = params("repository")

    redirect("/%s/%s/settings/options".format(owner, repository))
  })
  
  /**
   * Display the Options page.
   */
  get("/:owner/:repository/settings/options")(ownerOnly {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case Some(r) => settings.html.options(r)
      case None    => NotFound()
    }
  })
  
  /**
   * Save the repository options.
   */
  post("/:owner/:repository/settings/options", optionsForm)(ownerOnly { form =>
    val owner      = params("owner")
    val repository = params("repository")
    
    // save repository options
    saveRepositoryOptions(owner, repository, form.description, form.defaultBranch, form.repositoryType)
    
    redirect("%s/%s/settings/options".format(owner, repository))
  })
  
  /**
   * Display the Collaborators page.
   */
  get("/:owner/:repository/settings/collaborators")(ownerOnly {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case Some(r) => settings.html.collaborators(getCollaborators(owner, repository), r)
      case None    => NotFound()
    }
  })

  /**
   * Add the collaborator.
   */
  post("/:owner/:repository/settings/collaborators/add", collaboratorForm)(ownerOnly { form =>
    val owner      = params("owner")
    val repository = params("repository")

    addCollaborator(owner, repository, form.userName)
    redirect("/%s/%s/settings/collaborators".format(owner, repository))
  })

  /**
   * Add the collaborator.
   */
  get("/:owner/:repository/settings/collaborators/remove")(ownerOnly {
    val owner      = params("owner")
    val repository = params("repository")
    val userName   = params("name")

    removeCollaborator(owner, repository, userName)
    redirect("/%s/%s/settings/collaborators".format(owner, repository))
  })

  /**
   * Display the delete repository page.
   */
  get("/:owner/:repository/settings/delete")(ownerOnly {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case Some(r) => settings.html.delete(r)
      case None    => NotFound()
    }
  })

  /**
   * Delete the repository.
   */
  post("/:owner/:repository/settings/delete")(ownerOnly {
    val owner      = params("owner")
    val repository = params("repository")

    deleteRepository(owner, repository)

    FileUtils.deleteDirectory(getRepositoryDir(owner, repository))
    FileUtils.deleteDirectory(getWikiRepositoryDir(owner, repository))
    FileUtils.deleteDirectory(getWikiWorkDir(owner, repository))

    redirect("/%s".format(owner))
  })

  /**
   * Provides Constraint to validate the collaborator name.
   */
  def collaborator: Constraint = new Constraint(){
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