package gitbucket.core.controller

import java.io.FileInputStream

import gitbucket.core.admin.html
import gitbucket.core.service.{AccountService, RepositoryService, SystemSettingsService}
import gitbucket.core.util.{AdminAuthenticator, Mailer}
import gitbucket.core.ssh.SshServer
import gitbucket.core.plugin.{PluginInfoBase, PluginRegistry, PluginRepository}
import SystemSettingsService._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Directory._
import gitbucket.core.util.StringUtil._
import io.github.gitbucket.scalatra.forms._
import org.apache.commons.io.{FileUtils, IOUtils}
import org.scalatra.i18n.Messages
import com.github.zafarkhaja.semver.{Version => Semver}
import gitbucket.core.GitBucketCoreModule
import scala.collection.JavaConverters._


class SystemSettingsController extends SystemSettingsControllerBase
  with AccountService with RepositoryService with AdminAuthenticator

trait SystemSettingsControllerBase extends AccountManagementControllerBase {
  self: AccountService with RepositoryService with AdminAuthenticator =>

  private val form = mapping(
    "baseUrl"                  -> trim(label("Base URL", optional(text()))),
    "information"              -> trim(label("Information", optional(text()))),
    "allowAccountRegistration" -> trim(label("Account registration", boolean())),
    "allowAnonymousAccess"     -> trim(label("Anonymous access", boolean())),
    "isCreateRepoOptionPublic" -> trim(label("Default option to create a new repository", boolean())),
    "gravatar"                 -> trim(label("Gravatar", boolean())),
    "notification"             -> trim(label("Notification", boolean())),
    "activityLogLimit"         -> trim(label("Limit of activity logs", optional(number()))),
    "ssh"                      -> trim(label("SSH access", boolean())),
    "sshHost"                  -> trim(label("SSH host", optional(text()))),
    "sshPort"                  -> trim(label("SSH port", optional(number()))),
    "useSMTP"                  -> trim(label("SMTP", boolean())),
    "smtp"                     -> optionalIfNotChecked("useSMTP", mapping(
        "host"                     -> trim(label("SMTP Host", text(required))),
        "port"                     -> trim(label("SMTP Port", optional(number()))),
        "user"                     -> trim(label("SMTP User", optional(text()))),
        "password"                 -> trim(label("SMTP Password", optional(text()))),
        "ssl"                      -> trim(label("Enable SSL", optional(boolean()))),
        "starttls"                 -> trim(label("Enable STARTTLS", optional(boolean()))),
        "fromAddress"              -> trim(label("FROM Address", optional(text()))),
        "fromName"                 -> trim(label("FROM Name", optional(text())))
    )(Smtp.apply)),
    "ldapAuthentication"       -> trim(label("LDAP", boolean())),
    "ldap"                     -> optionalIfNotChecked("ldapAuthentication", mapping(
        "host"                     -> trim(label("LDAP host", text(required))),
        "port"                     -> trim(label("LDAP port", optional(number()))),
        "bindDN"                   -> trim(label("Bind DN", optional(text()))),
        "bindPassword"             -> trim(label("Bind Password", optional(text()))),
        "baseDN"                   -> trim(label("Base DN", text(required))),
        "userNameAttribute"        -> trim(label("User name attribute", text(required))),
        "additionalFilterCondition"-> trim(label("Additional filter condition", optional(text()))),
        "fullNameAttribute"        -> trim(label("Full name attribute", optional(text()))),
        "mailAttribute"            -> trim(label("Mail address attribute", optional(text()))),
        "tls"                      -> trim(label("Enable TLS", optional(boolean()))),
        "ssl"                      -> trim(label("Enable SSL", optional(boolean()))),
        "keystore"                 -> trim(label("Keystore", optional(text())))
    )(Ldap.apply)),
    "skinName" -> trim(label("AdminLTE skin name", text(required)))
  )(SystemSettings.apply).verifying { settings =>
    Vector(
      if(settings.ssh && settings.baseUrl.isEmpty){
        Some("baseUrl" -> "Base URL is required if SSH access is enabled.")
      } else None,
      if(settings.ssh && settings.sshHost.isEmpty){
        Some("sshHost" -> "SSH host is required if SSH access is enabled.")
      } else None
    ).flatten
  }

  private val sendMailForm = mapping(
    "smtp"        -> mapping(
      "host"        -> trim(label("SMTP Host", text(required))),
      "port"        -> trim(label("SMTP Port", optional(number()))),
      "user"        -> trim(label("SMTP User", optional(text()))),
      "password"    -> trim(label("SMTP Password", optional(text()))),
      "ssl"         -> trim(label("Enable SSL", optional(boolean()))),
      "starttls"    -> trim(label("Enable STARTTLS", optional(boolean()))),
      "fromAddress" -> trim(label("FROM Address", optional(text()))),
      "fromName"    -> trim(label("FROM Name", optional(text())))
    )(Smtp.apply),
    "testAddress" -> trim(label("", text(required)))
  )(SendMailForm.apply)

  case class SendMailForm(smtp: Smtp, testAddress: String)

  case class DataExportForm(tableNames: List[String])

  case class NewUserForm(userName: String, password: String, fullName: String,
                         mailAddress: String, isAdmin: Boolean,
                         description: Option[String], url: Option[String], fileId: Option[String])

  case class EditUserForm(userName: String, password: Option[String], fullName: String,
                          mailAddress: String, isAdmin: Boolean, description: Option[String], url: Option[String],
                          fileId: Option[String], clearImage: Boolean, isRemoved: Boolean)

  case class NewGroupForm(groupName: String, description: Option[String], url: Option[String], fileId: Option[String],
                          members: String)

  case class EditGroupForm(groupName: String, description: Option[String], url: Option[String], fileId: Option[String],
                           members: String, clearImage: Boolean, isRemoved: Boolean)


  val newUserForm = mapping(
    "userName"    -> trim(label("Username"     ,text(required, maxlength(100), identifier, uniqueUserName, reservedNames))),
    "password"    -> trim(label("Password"     ,text(required, maxlength(20), password))),
    "fullName"    -> trim(label("Full Name"    ,text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" ,text(required, maxlength(100), uniqueMailAddress()))),
    "isAdmin"     -> trim(label("User Type"    ,boolean())),
    "description" -> trim(label("bio"          ,optional(text()))),
    "url"         -> trim(label("URL"          ,optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      ,optional(text())))
  )(NewUserForm.apply)

  val editUserForm = mapping(
    "userName"    -> trim(label("Username"     ,text(required, maxlength(100), identifier))),
    "password"    -> trim(label("Password"     ,optional(text(maxlength(20), password)))),
    "fullName"    -> trim(label("Full Name"    ,text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" ,text(required, maxlength(100), uniqueMailAddress("userName")))),
    "isAdmin"     -> trim(label("User Type"    ,boolean())),
    "description" -> trim(label("bio"          ,optional(text()))),
    "url"         -> trim(label("URL"          ,optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      ,optional(text()))),
    "clearImage"  -> trim(label("Clear image"  ,boolean())),
    "removed"     -> trim(label("Disable"      ,boolean(disableByNotYourself("userName"))))
  )(EditUserForm.apply)

  val newGroupForm = mapping(
    "groupName" -> trim(label("Group name" ,text(required, maxlength(100), identifier, uniqueUserName, reservedNames))),
    "description" -> trim(label("Group description", optional(text()))),
    "url"       -> trim(label("URL"        ,optional(text(maxlength(200))))),
    "fileId"    -> trim(label("File ID"    ,optional(text()))),
    "members"   -> trim(label("Members"    ,text(required, members)))
  )(NewGroupForm.apply)

  val editGroupForm = mapping(
    "groupName"  -> trim(label("Group name"  ,text(required, maxlength(100), identifier))),
    "description" -> trim(label("Group description", optional(text()))),
    "url"        -> trim(label("URL"         ,optional(text(maxlength(200))))),
    "fileId"     -> trim(label("File ID"     ,optional(text()))),
    "members"    -> trim(label("Members"     ,text(required, members))),
    "clearImage" -> trim(label("Clear image" ,boolean())),
    "removed"    -> trim(label("Disable"     ,boolean()))
  )(EditGroupForm.apply)


  get("/admin/system")(adminOnly {
    html.system(flash.get("info"))
  })

  post("/admin/system", form)(adminOnly { form =>
    saveSystemSettings(form)

    if (form.sshAddress != context.settings.sshAddress) {
      SshServer.stop()
       for {
         sshAddress <- form.sshAddress
         baseUrl    <- form.baseUrl
       }
       SshServer.start(sshAddress, baseUrl)
    }

    flash += "info" -> "System settings has been updated."
    redirect("/admin/system")
  })

  post("/admin/system/sendmail", sendMailForm)(adminOnly { form =>
    try {
      new Mailer(form.smtp).send(form.testAddress,
        "Test message from GitBucket", "This is a test message from GitBucket.",
        context.loginAccount.get)

      "Test mail has been sent to: " + form.testAddress

    } catch {
      case e: Exception => "[Error] " + e.toString
    }
  })

  get("/admin/plugins")(adminOnly {
    // Installed plugins
    val enabledPlugins = PluginRegistry().getPlugins()

    val gitbucketVersion = Semver.valueOf(GitBucketCoreModule.getVersions.asScala.last.getVersion)

    // Plugins in the local repository
    val repositoryPlugins = PluginRepository.getPlugins()
      .filterNot { meta =>
        enabledPlugins.exists { plugin => plugin.pluginId == meta.id &&
          Semver.valueOf(plugin.pluginVersion).greaterThanOrEqualTo(Semver.valueOf(meta.latestVersion.version))
        }
      }.map { meta =>
        (meta, meta.versions.reverse.find { version => gitbucketVersion.satisfies(version.range) })
      }.collect { case (meta, Some(version)) =>
        new PluginInfoBase(
          pluginId      = meta.id,
          pluginName    = meta.name,
          pluginVersion = version.version,
          description   = meta.description
        )
      }

    // Merge
    val plugins = enabledPlugins.map((_, true)) ++ repositoryPlugins.map((_, false))

    html.plugins(plugins, flash.get("info"))
  })

  post("/admin/plugins/_reload")(adminOnly {
    PluginRegistry.reload(request.getServletContext(), loadSystemSettings(), request2Session(request).conn)
    flash += "info" -> "All plugins were reloaded."
    redirect("/admin/plugins")
  })

  post("/admin/plugins/:pluginId/:version/_uninstall")(adminOnly {
    val pluginId = params("pluginId")
    val version  = params("version")
    PluginRegistry().getPlugins()
      .collect { case plugin if (plugin.pluginId == pluginId && plugin.pluginVersion == version) => plugin }
      .foreach { _ =>
        PluginRegistry.uninstall(pluginId, request.getServletContext, loadSystemSettings(), request2Session(request).conn)
        flash += "info" -> s"${pluginId} was uninstalled."
      }
    redirect("/admin/plugins")
  })

  post("/admin/plugins/:pluginId/:version/_install")(adminOnly {
    val pluginId = params("pluginId")
    val version  = params("version")
    /// TODO!!!!
    PluginRepository.getPlugins()
      .collect { case meta if meta.id == pluginId => (meta, meta.versions.find(_.version == version) )}
      .foreach { case (meta, version) =>
        version.foreach { version =>
          // TODO Install version!
          PluginRegistry.install(
            new java.io.File(PluginHome, s".repository/${version.file}"),
            request.getServletContext,
            loadSystemSettings(),
            request2Session(request).conn
          )
          flash += "info" -> s"${pluginId} was installed."
        }
      }
    redirect("/admin/plugins")
  })


  get("/admin/users")(adminOnly {
    val includeRemoved = params.get("includeRemoved").map(_.toBoolean).getOrElse(false)
    val users          = getAllUsers(includeRemoved)
    val members        = users.collect { case account if(account.isGroupAccount) =>
      account.userName -> getGroupMembers(account.userName).map(_.userName)
    }.toMap

    html.userlist(users, members, includeRemoved)
  })

  get("/admin/users/_newuser")(adminOnly {
    html.user(None)
  })

  post("/admin/users/_newuser", newUserForm)(adminOnly { form =>
    createAccount(form.userName, sha1(form.password), form.fullName, form.mailAddress, form.isAdmin, form.description, form.url)
    updateImage(form.userName, form.fileId, false)
    redirect("/admin/users")
  })

  get("/admin/users/:userName/_edituser")(adminOnly {
    val userName = params("userName")
    html.user(getAccountByUserName(userName, true), flash.get("error"))
  })

  post("/admin/users/:name/_edituser", editUserForm)(adminOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName, true).map { account =>
      if(account.isAdmin && (form.isRemoved || !form.isAdmin) && isLastAdministrator(account)){
        flash += "error" -> "Account can't be turned off because this is last one administrator."
        redirect(s"/admin/users/${userName}/_edituser")
      } else {
        if(form.isRemoved){
          // Remove repositories
          //        getRepositoryNamesOfUser(userName).foreach { repositoryName =>
          //          deleteRepository(userName, repositoryName)
          //          FileUtils.deleteDirectory(getRepositoryDir(userName, repositoryName))
          //          FileUtils.deleteDirectory(getWikiRepositoryDir(userName, repositoryName))
          //          FileUtils.deleteDirectory(getTemporaryDir(userName, repositoryName))
          //        }
          // Remove from GROUP_MEMBER and COLLABORATOR
          removeUserRelatedData(userName)
        }

        updateAccount(account.copy(
          password     = form.password.map(sha1).getOrElse(account.password),
          fullName     = form.fullName,
          mailAddress  = form.mailAddress,
          isAdmin      = form.isAdmin,
          description  = form.description,
          url          = form.url,
          isRemoved    = form.isRemoved))

        updateImage(userName, form.fileId, form.clearImage)

        // call hooks
        if(form.isRemoved) PluginRegistry().getAccountHooks.foreach(_.deleted(userName))

        redirect("/admin/users")
      }
    } getOrElse NotFound()
  })

  get("/admin/users/_newgroup")(adminOnly {
    html.usergroup(None, Nil)
  })

  post("/admin/users/_newgroup", newGroupForm)(adminOnly { form =>
    createGroup(form.groupName, form.description, form.url)
    updateGroupMembers(form.groupName, form.members.split(",").map {
      _.split(":") match {
        case Array(userName, isManager) => (userName, isManager.toBoolean)
      }
    }.toList)
    updateImage(form.groupName, form.fileId, false)
    redirect("/admin/users")
  })

  get("/admin/users/:groupName/_editgroup")(adminOnly {
    defining(params("groupName")){ groupName =>
      html.usergroup(getAccountByUserName(groupName, true), getGroupMembers(groupName))
    }
  })

  post("/admin/users/:groupName/_editgroup", editGroupForm)(adminOnly { form =>
    defining(params("groupName"), form.members.split(",").map {
      _.split(":") match {
        case Array(userName, isManager) => (userName, isManager.toBoolean)
      }
    }.toList){ case (groupName, members) =>
      getAccountByUserName(groupName, true).map { account =>
        updateGroup(groupName, form.description, form.url, form.isRemoved)

        if(form.isRemoved){
          // Remove from GROUP_MEMBER
          updateGroupMembers(form.groupName, Nil)
//          // Remove repositories
//          getRepositoryNamesOfUser(form.groupName).foreach { repositoryName =>
//            deleteRepository(groupName, repositoryName)
//            FileUtils.deleteDirectory(getRepositoryDir(groupName, repositoryName))
//            FileUtils.deleteDirectory(getWikiRepositoryDir(groupName, repositoryName))
//            FileUtils.deleteDirectory(getTemporaryDir(groupName, repositoryName))
//          }
        } else {
          // Update GROUP_MEMBER
          updateGroupMembers(form.groupName, members)
//          // Update COLLABORATOR for group repositories
//          getRepositoryNamesOfUser(form.groupName).foreach { repositoryName =>
//            removeCollaborators(form.groupName, repositoryName)
//            members.foreach { case (userName, isManager) =>
//              addCollaborator(form.groupName, repositoryName, userName)
//            }
//          }
        }

        updateImage(form.groupName, form.fileId, form.clearImage)
        redirect("/admin/users")

      } getOrElse NotFound()
    }
  })

  get("/admin/data")(adminOnly {
    import gitbucket.core.util.JDBCUtil._
    val session = request2Session(request)
    html.data(session.conn.allTableNames())
  })

  post("/admin/export")(adminOnly {
    import gitbucket.core.util.JDBCUtil._
    val file = request2Session(request).conn.exportAsSQL(request.getParameterValues("tableNames").toSeq)

    contentType = "application/octet-stream"
    response.setHeader("Content-Disposition", "attachment; filename=" + file.getName)
    response.setContentLength(file.length.toInt)

    using(new FileInputStream(file)){ in =>
      IOUtils.copy(in, response.outputStream)
    }

    ()
  })

  private def members: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      if(value.split(",").exists {
        _.split(":") match { case Array(userName, isManager) => isManager.toBoolean }
      }) None else Some("Must select one manager at least.")
    }
  }

  protected def disableByNotYourself(paramName: String): Constraint = new Constraint() {
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      params.get(paramName).flatMap { userName =>
        if(userName == context.loginAccount.get.userName && params.get("removed") == Some("true"))
          Some("You can't disable your account yourself")
        else
          None
      }
    }
  }

}
