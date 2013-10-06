package app

import service._
import util.Directory._
import util.{UsersAuthenticator, OwnerAuthenticator}
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.scalatra.FlashMapSupport
import service.WebHookService.WebHookPayload
import util.JGitUtil.CommitInfo
import util.ControlUtil._
import org.eclipse.jgit.api.Git

class RepositorySettingsController extends RepositorySettingsControllerBase
  with RepositoryService with AccountService with WebHookService
  with OwnerAuthenticator with UsersAuthenticator

trait RepositorySettingsControllerBase extends ControllerBase with FlashMapSupport {
  self: RepositoryService with AccountService with WebHookService
    with OwnerAuthenticator with UsersAuthenticator =>

  // for repository options
  case class OptionsForm(description: Option[String], defaultBranch: String, isPrivate: Boolean)
  
  val optionsForm = mapping(
    "description"   -> trim(label("Description"    , optional(text()))),
    "defaultBranch" -> trim(label("Default Branch" , text(required, maxlength(100)))),
    "isPrivate"     -> trim(label("Repository Type", boolean()))
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
    saveRepositoryOptions(
      repository.owner,
      repository.name,
      form.description,
      form.defaultBranch,
      repository.repository.parentUserName.map { _ =>
        repository.repository.isPrivate
      } getOrElse form.isPrivate
    )
    flash += "info" -> "Repository settings has been updated."
    redirect(s"/${repository.owner}/${repository.name}/settings/options")
  })
  
  /**
   * Display the Collaborators page.
   */
  get("/:owner/:repository/settings/collaborators")(ownerOnly { repository =>
    settings.html.collaborators(
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
    settings.html.hooks(getWebHookURLs(repository.owner, repository.name), repository, flash.get("info"))
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
  get("/:owner/:repository/settings/hooks/test")(ownerOnly { repository =>
    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      import scala.collection.JavaConverters._
      val commits = git.log
        .add(git.getRepository.resolve(repository.repository.defaultBranch))
        .setMaxCount(3)
        .call.iterator.asScala.map(new CommitInfo(_))

      val webHookURLs = getWebHookURLs(repository.owner, repository.name)
      if(webHookURLs.nonEmpty){
        callWebHook(repository.owner, repository.name, webHookURLs,
          WebHookPayload(
            git,
            "refs/heads/" + repository.repository.defaultBranch,
            repository,
            commits.toList,
            getAccountByUserName(repository.owner).get))
      }

      flash += "info" -> "Test payload deployed!"
    }
    redirect(s"/${repository.owner}/${repository.name}/settings/hooks")
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
   * Provides duplication check for web hook url.
   */
  private def webHook: Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] =
      getWebHookURLs(params("owner"), params("repository")).map(_.url).find(_ == value).map(_ => "URL had been registered already.")
  }

  /**
   * Provides Constraint to validate the collaborator name.
   */
  private def collaborator: Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] =
      getAccountByUserName(value) match {
        case None => Some("User does not exist.")
        case Some(x) if(x.userName == params("owner") || getCollaborators(params("owner"), params("repository")).contains(x.userName))
                  => Some("User can access this repository already.")
        case _    => None
      }
  }

}