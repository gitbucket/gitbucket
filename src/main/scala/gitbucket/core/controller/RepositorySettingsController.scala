package gitbucket.core.controller

import gitbucket.core.settings.html
import gitbucket.core.model.WebHook
import gitbucket.core.service.{RepositoryService, AccountService, WebHookService}
import gitbucket.core.service.WebHookService._
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
import scala.util.{Success, Failure}
import org.eclipse.jgit.lib.ObjectId


class RepositorySettingsController extends RepositorySettingsControllerBase
  with RepositoryService with AccountService with WebHookService
  with OwnerAuthenticator with UsersAuthenticator

trait RepositorySettingsControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with WebHookService
    with OwnerAuthenticator with UsersAuthenticator =>

  // for repository options
  case class OptionsForm(repositoryName: String, description: Option[String], defaultBranch: String, isPrivate: Boolean)
  
  val optionsForm = mapping(
    "repositoryName" -> trim(label("Repository Name", text(required, maxlength(40), identifier, renameRepositoryName))),
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
  case class WebHookForm(url: String, events: Set[WebHook.Event])

  def webHookForm(update:Boolean) = mapping(
    "url"    -> trim(label("url", text(required, webHook(update)))),
    "events" -> webhookEvents
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
    html.hooks(getWebHooks(repository.owner, repository.name), repository, flash.get("info"))
  })

  /**
   * Display the web hook edit page.
   */
  get("/:owner/:repository/settings/hooks/new")(ownerOnly { repository =>
    val webhook = WebHook(repository.owner, repository.name, "")
    html.edithooks(webhook, Set(WebHook.Push), repository, flash.get("info"), true)
  })

  /**
   * Add the web hook URL.
   */
  post("/:owner/:repository/settings/hooks/new", webHookForm(false))(ownerOnly { (form, repository) =>
    addWebHook(repository.owner, repository.name, form.url, form.events)
    flash += "info" -> s"Webhook ${form.url} created"
    redirect(s"/${repository.owner}/${repository.name}/settings/hooks")
  })

  /**
   * Delete the web hook URL.
   */
  get("/:owner/:repository/settings/hooks/delete")(ownerOnly { repository =>
    deleteWebHook(repository.owner, repository.name, params("url"))
    flash += "info" -> s"Webhook ${params("url")} deleted"
    redirect(s"/${repository.owner}/${repository.name}/settings/hooks")
  })

  /**
   * Send the test request to registered web hook URLs.
   */
  ajaxPost("/:owner/:repository/settings/hooks/test")(ownerOnly { repository =>
    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      import scala.collection.JavaConverters._
      import scala.concurrent.duration._
      import scala.concurrent._
      import scala.util.control.NonFatal
      import org.apache.http.util.EntityUtils
      import scala.concurrent.ExecutionContext.Implicits.global

      val url = params("url")
      val dummyPayload = {
        val ownerAccount = getAccountByUserName(repository.owner).get
        val commits = if(repository.commitCount == 0) List.empty else git.log
          .add(git.getRepository.resolve(repository.repository.defaultBranch))
          .setMaxCount(4)
          .call.iterator.asScala.map(new CommitInfo(_)).toList
        val pushedCommit = commits.drop(1)
        WebHookPushPayload(git, ownerAccount, "refs/heads/" + repository.repository.defaultBranch, repository, pushedCommit, ownerAccount,
                           oldId = commits.lastOption.map(_.id).map(ObjectId.fromString).getOrElse(ObjectId.zeroId()),
                           newId = commits.headOption.map(_.id).map(ObjectId.fromString).getOrElse(ObjectId.zeroId()))
      }
      val dummyWebHookInfo = WebHook(repository.owner, repository.name, url)

      val (webHook, json, reqFuture, resFuture) = callWebHook(WebHook.Push, List(dummyWebHookInfo), dummyPayload).head

      def headers(h: Array[org.apache.http.Header]): Array[Array[String]] = h.map{ h => Array(h.getName, h.getValue) }
      val toErrorMap:PartialFunction[Throwable, Map[String,String]] = {
        case e:java.net.UnknownHostException => Map("error"-> ("Unknown host "+ e.getMessage))
        case e:java.lang.IllegalArgumentException => Map("error"-> ("invalid url"))
        case e:org.apache.http.client.ClientProtocolException => Map("error"-> ("invalid url"))
        case NonFatal(e) => Map("error"-> (e.getClass + " "+ e.getMessage))
      }
      contentType = formats("json")
      var result = Map(
        "url" -> url,
        "request"  -> Await.result(reqFuture.map(req => Map(
          "headers"   -> headers(req.getAllHeaders),
          "payload"   -> json
        )).recover(toErrorMap), 20 seconds),
        "responce" -> Await.result(resFuture.map(res => Map(
            "status"  -> res.getStatusLine(),
            "body"    -> EntityUtils.toString(res.getEntity()),
            "headers" -> headers(res.getAllHeaders())
        )).recover(toErrorMap), 20 seconds))
      org.json4s.jackson.Serialization.write(result)
    }
  })

  /**
   * Display the web hook edit page.
   */
  get("/:owner/:repository/settings/hooks/edit")(ownerOnly { repository =>
    getWebHook(repository.owner, repository.name, params("url")).map{ case (webhook, events) =>
      html.edithooks(webhook, events, repository, flash.get("info"), false)
    } getOrElse NotFound
  })

  /**
   * Update web hook settings.
   */
  post("/:owner/:repository/settings/hooks/edit", webHookForm(true))(ownerOnly { (form, repository) =>
    updateWebHook(repository.owner, repository.name, form.url, form.events)
    flash += "info" -> s"webhook ${form.url} updated"
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
  private def webHook(needExists: Boolean): Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(getWebHook(params("owner"), params("repository"), value).isDefined != needExists){
        Some(if(needExists){
          "URL had not been registered yet."
        } else {
          "URL had been registered already."
        })
      } else {
        None
      }
  }

  private def webhookEvents = new ValueType[Set[WebHook.Event]]{
    def convert(name: String, params: Map[String, String], messages: Messages): Set[WebHook.Event] = {
      WebHook.Event.values.flatMap { t =>
        params.get(name + "." + t.name).map(_ => t)
      }.toSet
    }
    def validate(name: String, params: Map[String, String], messages: Messages): Seq[(String, String)] = if(convert(name,params,messages).isEmpty){
      Seq(name -> messages("error.required").format(name))
    } else {
      Nil
    }
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
