package gitbucket.core.controller

import gitbucket.core.settings.html
import gitbucket.core.model.{WebHook, RepositoryWebHook}
import gitbucket.core.service._
import gitbucket.core.service.WebHookService._
import gitbucket.core.util._
import gitbucket.core.util.JGitUtil._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.Directory._
import io.github.gitbucket.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.scalatra.i18n.Messages
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import gitbucket.core.model.WebHookContentType
import gitbucket.core.plugin.PluginRegistry


class RepositorySettingsController extends RepositorySettingsControllerBase
  with RepositoryService with AccountService with WebHookService with ProtectedBranchService with CommitStatusService with DeployKeyService
  with OwnerAuthenticator with UsersAuthenticator

trait RepositorySettingsControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with WebHookService with ProtectedBranchService with CommitStatusService with DeployKeyService
    with OwnerAuthenticator with UsersAuthenticator =>

  // for repository options
  case class OptionsForm(
    repositoryName: String,
    description: Option[String],
    isPrivate: Boolean,
    issuesOption: String,
    externalIssuesUrl: Option[String],
    wikiOption: String,
    externalWikiUrl: Option[String],
    allowFork: Boolean
  )

  val optionsForm = mapping(
    "repositoryName"    -> trim(label("Repository Name"    , text(required, maxlength(100), repository, renameRepositoryName))),
    "description"       -> trim(label("Description"        , optional(text()))),
    "isPrivate"         -> trim(label("Repository Type"    , boolean())),
    "issuesOption"      -> trim(label("Issues Option"      , text(required, featureOption))),
    "externalIssuesUrl" -> trim(label("External Issues URL", optional(text(maxlength(200))))),
    "wikiOption"        -> trim(label("Wiki Option"        , text(required, featureOption))),
    "externalWikiUrl"   -> trim(label("External Wiki URL"  , optional(text(maxlength(200))))),
    "allowFork"         -> trim(label("Allow Forking"      , boolean()))
  )(OptionsForm.apply)

  // for default branch
  case class DefaultBranchForm(defaultBranch: String)

  val defaultBranchForm = mapping(
    "defaultBranch"  -> trim(label("Default Branch" , text(required, maxlength(100))))
  )(DefaultBranchForm.apply)


  // for deploy key
  case class DeployKeyForm(title: String, publicKey: String, allowWrite: Boolean)

  val deployKeyForm = mapping(
    "title"      -> trim(label("Title", text(required, maxlength(100)))),
    "publicKey"  -> trim2(label("Key" , text(required))), // TODO duplication check in the repository?
    "allowWrite" -> trim(label("Key"  , boolean()))
  )(DeployKeyForm.apply)

  // for web hook url addition
  case class WebHookForm(url: String, events: Set[WebHook.Event], ctype: WebHookContentType, token: Option[String])

  def webHookForm(update:Boolean) = mapping(
    "url"    -> trim(label("url", text(required, webHook(update)))),
    "events" -> webhookEvents,
    "ctype" -> label("ctype", text()),
    "token" -> optional(trim(label("token", text(maxlength(100)))))
  )(
    (url, events, ctype, token) => WebHookForm(url, events, WebHookContentType.valueOf(ctype), token)
  )

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
    saveRepositoryOptions(
      repository.owner,
      repository.name,
      form.description,
      repository.repository.parentUserName.map { _ =>
        repository.repository.isPrivate
      } getOrElse form.isPrivate,
      form.issuesOption,
      form.externalIssuesUrl,
      form.wikiOption,
      form.externalWikiUrl,
      form.allowFork
    )
    // Change repository name
    if(repository.name != form.repositoryName){
      // Update database
      renameRepository(repository.owner, repository.name, repository.owner, form.repositoryName)
      // Move git repository
      defining(getRepositoryDir(repository.owner, repository.name)){ dir =>
        if(dir.isDirectory){
          FileUtils.moveDirectory(dir, getRepositoryDir(repository.owner, form.repositoryName))
        }
      }
      // Move wiki repository
      defining(getWikiRepositoryDir(repository.owner, repository.name)){ dir =>
        if(dir.isDirectory) {
          FileUtils.moveDirectory(dir, getWikiRepositoryDir(repository.owner, form.repositoryName))
        }
      }
      // Move files directory
      defining(getRepositoryFilesDir(repository.owner, repository.name)){ dir =>
        if(dir.isDirectory) {
          FileUtils.moveDirectory(dir, getRepositoryFilesDir(repository.owner, form.repositoryName))
        }
      }
      // Call hooks
      PluginRegistry().getRepositoryHooks.foreach(_.renamed(repository.owner, repository.name, form.repositoryName))
    }
    flash += "info" -> "Repository settings has been updated."
    redirect(s"/${repository.owner}/${form.repositoryName}/settings/options")
  })

  /** branch settings */
  get("/:owner/:repository/settings/branches")(ownerOnly { repository =>
    val protecteions = getProtectedBranchList(repository.owner, repository.name)
    html.branches(repository, protecteions, flash.get("info"))
  })

  /** Update default branch */
  post("/:owner/:repository/settings/update_default_branch", defaultBranchForm)(ownerOnly { (form, repository) =>
    if(!repository.branchList.contains(form.defaultBranch)){
      redirect(s"/${repository.owner}/${repository.name}/settings/options")
    } else {
      saveRepositoryDefaultBranch(repository.owner, repository.name, form.defaultBranch)
      // Change repository HEAD
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        git.getRepository.updateRef(Constants.HEAD, true).link(Constants.R_HEADS + form.defaultBranch)
      }
      flash += "info" -> "Repository default branch has been updated."
      redirect(s"/${repository.owner}/${repository.name}/settings/branches")
    }
  })

  /** Branch protection for branch */
  get("/:owner/:repository/settings/branches/:branch")(ownerOnly { repository =>
    import gitbucket.core.api._
    val branch = params("branch")
    if(!repository.branchList.contains(branch)){
      redirect(s"/${repository.owner}/${repository.name}/settings/branches")
    } else {
      val protection = ApiBranchProtection(getProtectedBranchInfo(repository.owner, repository.name, branch))
      val lastWeeks = getRecentStatuesContexts(repository.owner, repository.name, org.joda.time.LocalDateTime.now.minusWeeks(1).toDate).toSet
      val knownContexts = (lastWeeks ++ protection.status.contexts).toSeq.sortBy(identity)
      html.branchprotection(repository, branch, protection, knownContexts, flash.get("info"))
    }
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

  post("/:owner/:repository/settings/collaborators")(ownerOnly { repository =>
    val collaborators = params("collaborators")
    removeCollaborators(repository.owner, repository.name)
    collaborators.split(",").withFilter(_.nonEmpty).map { collaborator =>
      val userName :: role :: Nil = collaborator.split(":").toList
      addCollaborator(repository.owner, repository.name, userName, role)
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
    val webhook = RepositoryWebHook(repository.owner, repository.name, "", WebHookContentType.FORM, None)
    html.edithook(webhook, Set(WebHook.Push), repository, true)
  })

  /**
   * Add the web hook URL.
   */
  post("/:owner/:repository/settings/hooks/new", webHookForm(false))(ownerOnly { (form, repository) =>
    addWebHook(repository.owner, repository.name, form.url, form.events, form.ctype, form.token)
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
    def _headers(h: Array[org.apache.http.Header]): Array[Array[String]] = h.map { h => Array(h.getName, h.getValue) }

    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      import scala.collection.JavaConverters._
      import scala.concurrent.duration._
      import scala.concurrent._
      import scala.util.control.NonFatal
      import org.apache.http.util.EntityUtils
      import scala.concurrent.ExecutionContext.Implicits.global

      val url = params("url")
      val token = Some(params("token"))
      val ctype = WebHookContentType.valueOf(params("ctype"))
      val dummyWebHookInfo = RepositoryWebHook(repository.owner, repository.name, url, ctype, token)
      val dummyPayload = {
        val ownerAccount = getAccountByUserName(repository.owner).get
        val commits = if(JGitUtil.isEmpty(git)) List.empty else git.log
          .add(git.getRepository.resolve(repository.repository.defaultBranch))
          .setMaxCount(4)
          .call.iterator.asScala.map(new CommitInfo(_)).toList
        val pushedCommit = commits.drop(1)

        WebHookPushPayload(
          git = git,
          sender = ownerAccount,
          refName = "refs/heads/" + repository.repository.defaultBranch,
          repositoryInfo = repository,
          commits = pushedCommit,
          repositoryOwner = ownerAccount,
          oldId = commits.lastOption.map(_.id).map(ObjectId.fromString).getOrElse(ObjectId.zeroId()),
          newId = commits.headOption.map(_.id).map(ObjectId.fromString).getOrElse(ObjectId.zeroId())
        )
      }

      val (webHook, json, reqFuture, resFuture) = callWebHook(WebHook.Push, List(dummyWebHookInfo), dummyPayload).head

      val toErrorMap: PartialFunction[Throwable, Map[String,String]] = {
        case e: java.net.UnknownHostException => Map("error"-> ("Unknown host " + e.getMessage))
        case e: java.lang.IllegalArgumentException => Map("error"-> ("invalid url"))
        case e: org.apache.http.client.ClientProtocolException => Map("error"-> ("invalid url"))
        case NonFatal(e) => Map("error"-> (e.getClass + " "+ e.getMessage))
      }

      contentType = formats("json")
      org.json4s.jackson.Serialization.write(Map(
        "url" -> url,
        "request" -> Await.result(reqFuture.map(req => Map(
          "headers" -> _headers(req.getAllHeaders),
          "payload" -> json
        )).recover(toErrorMap), 20 seconds),
        "response" -> Await.result(resFuture.map(res => Map(
          "status"  -> res.getStatusLine(),
          "body"    -> EntityUtils.toString(res.getEntity()),
          "headers" -> _headers(res.getAllHeaders())
        )).recover(toErrorMap), 20 seconds)
      ))
    }
  })

  /**
   * Display the web hook edit page.
   */
  get("/:owner/:repository/settings/hooks/edit")(ownerOnly { repository =>
    getWebHook(repository.owner, repository.name, params("url")).map{ case (webhook, events) =>
      html.edithook(webhook, events, repository, false)
    } getOrElse NotFound()
  })

  /**
   * Update web hook settings.
   */
  post("/:owner/:repository/settings/hooks/edit", webHookForm(true))(ownerOnly { (form, repository) =>
    updateWebHook(repository.owner, repository.name, form.url, form.events, form.ctype, form.token)
    flash += "info" -> s"webhook ${form.url} updated"
    redirect(s"/${repository.owner}/${repository.name}/settings/hooks")
  })

  /**
   * Display the danger zone.
   */
  get("/:owner/:repository/settings/danger")(ownerOnly {
    html.danger(_, flash.get("info"))
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
          if(dir.isDirectory){
            FileUtils.moveDirectory(dir, getRepositoryDir(form.newOwner, repository.name))
          }
        }
        // Move wiki repository
        defining(getWikiRepositoryDir(repository.owner, repository.name)){ dir =>
          if(dir.isDirectory) {
            FileUtils.moveDirectory(dir, getWikiRepositoryDir(form.newOwner, repository.name))
          }
        }
        // Move lfs directory
        defining(getLfsDir(repository.owner, repository.name)){ dir =>
          if(dir.isDirectory()) {
            FileUtils.moveDirectory(dir, getLfsDir(form.newOwner, repository.name))
          }
        }
        // Move attached directory
        defining(getAttachedDir(repository.owner, repository.name)){ dir =>
          if(dir.isDirectory) {
            FileUtils.moveDirectory(dir, getAttachedDir(form.newOwner, repository.name))
          }
        }
        // Delete parent directory
        FileUtil.deleteDirectoryIfEmpty(getRepositoryFilesDir(repository.owner, repository.name))

        // Call hooks
        PluginRegistry().getRepositoryHooks.foreach(_.transferred(repository.owner, form.newOwner, repository.name))
      }
    }
    redirect(s"/${form.newOwner}/${repository.name}")
  })

  /**
   * Delete the repository.
   */
  post("/:owner/:repository/settings/delete")(ownerOnly { repository =>
    LockUtil.lock(s"${repository.owner}/${repository.name}"){
      // Delete the repository and related files
      deleteRepository(repository.owner, repository.name)

      FileUtils.deleteDirectory(getRepositoryDir(repository.owner, repository.name))
      FileUtils.deleteDirectory(getWikiRepositoryDir(repository.owner, repository.name))
      FileUtils.deleteDirectory(getTemporaryDir(repository.owner, repository.name))
      val lfsDir = getLfsDir(repository.owner, repository.name)
      FileUtils.deleteDirectory(lfsDir)
      FileUtil.deleteDirectoryIfEmpty(lfsDir.getParentFile())

      // Call hooks
      PluginRegistry().getRepositoryHooks.foreach(_.deleted(repository.owner, repository.name))
    }

    redirect(s"/${repository.owner}")
  })

  /**
   * Run GC
   */
  post("/:owner/:repository/settings/gc")(ownerOnly { repository =>
    LockUtil.lock(s"${repository.owner}/${repository.name}") {
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        git.gc()
      }
    }
    flash += "info" -> "Garbage collection has been executed."
    redirect(s"/${repository.owner}/${repository.name}/settings/danger")
  })

  /** List deploy keys */
  get("/:owner/:repository/settings/deploykey")(ownerOnly { repository =>
    html.deploykey(repository, getDeployKeys(repository.owner, repository.name))
  })

  /** Register a deploy key */
  post("/:owner/:repository/settings/deploykey", deployKeyForm)(ownerOnly { (form, repository) =>
    addDeployKey(repository.owner, repository.name, form.title, form.publicKey, form.allowWrite)
    redirect(s"/${repository.owner}/${repository.name}/settings/deploykey")
  })

  /** Delete a deploy key */
  get("/:owner/:repository/settings/deploykey/delete/:id")(ownerOnly { repository =>
    val deployKeyId = params("id").toInt
    deleteDeployKey(repository.owner, repository.name, deployKeyId)
    redirect(s"/${repository.owner}/${repository.name}/settings/deploykey")
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

//  /**
//   * Provides Constraint to validate the collaborator name.
//   */
//  private def collaborator: Constraint = new Constraint(){
//    override def validate(name: String, value: String, messages: Messages): Option[String] =
//      getAccountByUserName(value) match {
//        case None => Some("User does not exist.")
////        case Some(x) if(x.isGroupAccount)
////                  => Some("User does not exist.")
//        case Some(x) if(x.userName == params("owner") || getCollaborators(params("owner"), params("repository")).contains(x.userName))
//                  => Some(value + " is repository owner.") // TODO also group members?
//        case _    => None
//      }
//  }

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
   *
   */
  private def featureOption: Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Option[String] =
      if(Seq("DISABLE", "PRIVATE", "PUBLIC", "ALL").contains(value)) None else Some("Option is invalid.")
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
