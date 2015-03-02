package gitbucket.core.controller

import gitbucket.core.settings.html
import gitbucket.core.model.WebHook
import gitbucket.core.service.{RepositoryService, AccountService, WebHookService}
import gitbucket.core.service.WebHookService.WebHookPayload
import gitbucket.core.util._
import gitbucket.core.util.JGitUtil._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.Directory._
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.scalatra.i18n.Messages
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants

class RepositorySettingsController extends RepositorySettingsControllerBase
  with RepositoryService with AccountService with WebHookService
  with OwnerAuthenticator with UsersAuthenticator

trait RepositorySettingsControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with WebHookService
    with OwnerAuthenticator with UsersAuthenticator =>

  // for repository options
  case class OptionsForm(repositoryName: String, description: Option[String], defaultBranch: String, isPrivate: Boolean)
  
  val optionsForm = mapping(
    "repositoryName" -> trim(label("Description"    , text(required, maxlength(40), identifier, renameRepositoryName))),
    "description"    -> trim(label("Description"    , optional(text()))),
    "defaultBranch"  -> trim(label("Default Branch" , text(required, maxlength(100)))),
    "isPrivate"      -> trim(label("Repository Type", boolean()))
  )(OptionsForm.apply)

  // for collaborator addition
  case class CollaboratorForm(userName: String)

  val collaboratorForm = mapping(
    "userName" -> trim(label("Username", text(required, collaborator)))
  )(CollaboratorForm.apply)

  // for web hook url addition
  case class WebHookForm(url: String)

  val webHookForm = mapping(
    "url" -> trim(label("url", text(required, webHook)))
  )(WebHookForm.apply)

  // for transfer ownership
  case class TransferOwnerShipForm(newOwner: String)

  val transferForm = mapping(
    "newOwner" -> trim(label("New owner", text(required, transferUser)))
  )(TransferOwnerShipForm.apply)

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
    html.options(_, flash.get("info"))
  })
  
  /**
   * Save the repository options.
   */
  post("/:owner/:repository/settings/options", optionsForm)(ownerOnly { (form, repository) =>
    val defaultBranch = if(repository.branchList.isEmpty) "master" else form.defaultBranch
    saveRepositoryOptions(
      repository.owner,
      repository.name,
      form.description,
      defaultBranch,
      repository.repository.parentUserName.map { _ =>
        repository.repository.isPrivate
      } getOrElse form.isPrivate
    )
    // Change repository name
    if(repository.name != form.repositoryName){
      // Update database
      renameRepository(repository.owner, repository.name, repository.owner, form.repositoryName)
      // Move git repository
      defining(getRepositoryDir(repository.owner, repository.name)){ dir =>
        FileUtils.moveDirectory(dir, getRepositoryDir(repository.owner, form.repositoryName))
      }
      // Move wiki repository
      defining(getWikiRepositoryDir(repository.owner, repository.name)){ dir =>
        FileUtils.moveDirectory(dir, getWikiRepositoryDir(repository.owner, form.repositoryName))
      }
    }
    // Change repository HEAD
    using(Git.open(getRepositoryDir(repository.owner, form.repositoryName))) { git =>
      git.getRepository.updateRef(Constants.HEAD, true).link(Constants.R_HEADS + defaultBranch)
    }
    flash += "info" -> "Repository settings has been updated."
    redirect(s"/${repository.owner}/${form.repositoryName}/settings/options")
  })
  
  /**
   * Display the Collaborators page.
   */
  get("/:owner/:repository/settings/collaborators")(ownerOnly { repository =>
    html.collaborators(
      getCollaborators(repository.owner, repository.name),
      getAccountByUserName(repository.owner).get.isGroupAccount,
      repository)
  })

  /**
   * Add the collaborator.
   */
  post("/:owner/:repository/settings/collaborators/add", collaboratorForm)(ownerOnly { (form, repository) =>
    if(!getAccountByUserName(repository.owner).get.isGroupAccount){
      addCollaborator(repository.owner, repository.name, form.userName)
    }
    redirect(s"/${repository.owner}/${repository.name}/settings/collaborators")
  })

  /**
   * Add the collaborator.
   */
  get("/:owner/:repository/settings/collaborators/remove")(ownerOnly { repository =>
    if(!getAccountByUserName(repository.owner).get.isGroupAccount){
      removeCollaborator(repository.owner, repository.name, params("name"))
    }
    redirect(s"/${repository.owner}/${repository.name}/settings/collaborators")
  })

  /**
   * Display the web hook page.
   */
  get("/:owner/:repository/settings/hooks")(ownerOnly { repository =>
    html.hooks(getWebHookURLs(repository.owner, repository.name), flash.get("url"), repository, flash.get("info"))
  })

  /**
   * Add the web hook URL.
   */
  post("/:owner/:repository/settings/hooks/add", webHookForm)(ownerOnly { (form, repository) =>
    addWebHookURL(repository.owner, repository.name, form.url)
    redirect(s"/${repository.owner}/${repository.name}/settings/hooks")
  })

  /**
   * Delete the web hook URL.
   */
  get("/:owner/:repository/settings/hooks/delete")(ownerOnly { repository =>
    deleteWebHookURL(repository.owner, repository.name, params("url"))
    redirect(s"/${repository.owner}/${repository.name}/settings/hooks")
  })

  /**
   * Send the test request to registered web hook URLs.
   */
  post("/:owner/:repository/settings/hooks/test", webHookForm)(ownerOnly { (form, repository) =>
    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      import scala.collection.JavaConverters._
      val commits = git.log
        .add(git.getRepository.resolve(repository.repository.defaultBranch))
        .setMaxCount(3)
        .call.iterator.asScala.map(new CommitInfo(_))

      getAccountByUserName(repository.owner).foreach { ownerAccount =>
        callWebHook(repository.owner, repository.name,
          List(WebHook(repository.owner, repository.name, form.url)),
          WebHookPayload(git, ownerAccount, "refs/heads/" + repository.repository.defaultBranch, repository, commits.toList, ownerAccount)
        )
      }
      flash += "url"  -> form.url
      flash += "info" -> "Test payload deployed!"
    }
    redirect(s"/${repository.owner}/${repository.name}/settings/hooks")
  })

  /**
   * Display the danger zone.
   */
  get("/:owner/:repository/settings/danger")(ownerOnly {
    html.danger(_)
  })

  /**
   * Transfer repository ownership.
   */
  post("/:owner/:repository/settings/transfer", transferForm)(ownerOnly { (form, repository) =>
    // Change repository owner
    if(repository.owner != form.newOwner){
      LockUtil.lock(s"${repository.owner}/${repository.name}"){
        // Update database
        renameRepository(repository.owner, repository.name, form.newOwner, repository.name)
        // Move git repository
        defining(getRepositoryDir(repository.owner, repository.name)){ dir =>
          FileUtils.moveDirectory(dir, getRepositoryDir(form.newOwner, repository.name))
        }
        // Move wiki repository
        defining(getWikiRepositoryDir(repository.owner, repository.name)){ dir =>
          FileUtils.moveDirectory(dir, getWikiRepositoryDir(form.newOwner, repository.name))
        }
      }
    }
    redirect(s"/${form.newOwner}/${repository.name}")
  })

  /**
   * Delete the repository.
   */
  post("/:owner/:repository/settings/delete")(ownerOnly { repository =>
    LockUtil.lock(s"${repository.owner}/${repository.name}"){
      deleteRepository(repository.owner, repository.name)

      FileUtils.deleteDirectory(getRepositoryDir(repository.owner, repository.name))
      FileUtils.deleteDirectory(getWikiRepositoryDir(repository.owner, repository.name))
      FileUtils.deleteDirectory(getTemporaryDir(repository.owner, repository.name))
    }
    redirect(s"/${repository.owner}")
  })

  /**
   * Provides duplication check for web hook url.
   */
  private def webHook: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      getWebHookURLs(params("owner"), params("repository")).map(_.url).find(_ == value).map(_ => "URL had been registered already.")
  }

  /**
   * Provides Constraint to validate the collaborator name.
   */
  private def collaborator: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      getAccountByUserName(value) match {
        case None => Some("User does not exist.")
        case Some(x) if(x.isGroupAccount)
                  => Some("User does not exist.")
        case Some(x) if(x.userName == params("owner") || getCollaborators(params("owner"), params("repository")).contains(x.userName))
                  => Some("User can access this repository already.")
        case _    => None
      }
  }

  /**
   * Duplicate check for the rename repository name.
   */
  private def renameRepositoryName: Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Option[String] =
      params.get("repository").filter(_ != value).flatMap { _ =>
        params.get("owner").flatMap { userName =>
          getRepositoryNamesOfUser(userName).find(_ == value).map(_ => "Repository already exists.")
        }
      }
  }

  /**
   * Provides Constraint to validate the repository transfer user.
   */
  private def transferUser: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      getAccountByUserName(value) match {
        case None    => Some("User does not exist.")
        case Some(x) => if(x.userName == params("owner")){
          Some("This is current repository owner.")
        } else {
          params.get("repository").flatMap { repositoryName =>
            getRepositoryNamesOfUser(x.userName).find(_ == repositoryName).map{ _ => "User already has same repository." }
          }
        }
      }
  }
}