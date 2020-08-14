package gitbucket.core.controller

import java.io.FileInputStream

import gitbucket.core.admin.html
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.SystemSettingsService._
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.ssh.SshServer
import gitbucket.core.util.Implicits._
import gitbucket.core.util.StringUtil._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.{AdminAuthenticator, Mailer}
import org.apache.commons.io.IOUtils
import org.apache.commons.mail.EmailException
import org.json4s.jackson.Serialization
import org.scalatra._
import org.scalatra.forms._
import org.scalatra.i18n.Messages

import scala.collection.mutable.ListBuffer
import scala.util.Using

class SystemSettingsController
    extends SystemSettingsControllerBase
    with AccountService
    with RepositoryService
    with AdminAuthenticator

case class Table(name: String, columns: Seq[Column])
case class Column(name: String, primaryKey: Boolean)

trait SystemSettingsControllerBase extends AccountManagementControllerBase {
  self: AccountService with RepositoryService with AdminAuthenticator =>

  private val form = mapping(
    "baseUrl" -> trim(label("Base URL", optional(text()))),
    "information" -> trim(label("Information", optional(text()))),
    "allowAccountRegistration" -> trim(label("Account registration", boolean())),
    "allowAnonymousAccess" -> trim(label("Anonymous access", boolean())),
    "isCreateRepoOptionPublic" -> trim(label("Default visibility of new repository", boolean())),
    "repositoryOperation" -> mapping(
      "create" -> trim(label("Allow all users to create repository", boolean())),
      "delete" -> trim(label("Allow all users to delete repository", boolean())),
      "rename" -> trim(label("Allow all users to rename repository", boolean())),
      "transfer" -> trim(label("Allow all users to transfer repository", boolean())),
      "fork" -> trim(label("Allow all users to fork repository", boolean()))
    )(RepositoryOperation.apply),
    "gravatar" -> trim(label("Gravatar", boolean())),
    "notification" -> trim(label("Notification", boolean())),
    "limitVisibleRepositories" -> trim(label("limitVisibleRepositories", boolean())),
    "ssh" -> mapping(
      "enabled" -> trim(label("SSH access", boolean())),
      "host" -> trim(label("SSH host", optional(text()))),
      "port" -> trim(label("SSH port", optional(number())))
    )(Ssh.apply),
    "useSMTP" -> trim(label("SMTP", boolean())),
    "smtp" -> optionalIfNotChecked(
      "useSMTP",
      mapping(
        "host" -> trim(label("SMTP Host", text(required))),
        "port" -> trim(label("SMTP Port", optional(number()))),
        "user" -> trim(label("SMTP User", optional(text()))),
        "password" -> trim(label("SMTP Password", optional(text()))),
        "ssl" -> trim(label("Enable SSL", optional(boolean()))),
        "starttls" -> trim(label("Enable STARTTLS", optional(boolean()))),
        "fromAddress" -> trim(label("FROM Address", optional(text()))),
        "fromName" -> trim(label("FROM Name", optional(text())))
      )(Smtp.apply)
    ),
    "ldapAuthentication" -> trim(label("LDAP", boolean())),
    "ldap" -> optionalIfNotChecked(
      "ldapAuthentication",
      mapping(
        "host" -> trim(label("LDAP host", text(required))),
        "port" -> trim(label("LDAP port", optional(number()))),
        "bindDN" -> trim(label("Bind DN", optional(text()))),
        "bindPassword" -> trim(label("Bind Password", optional(text()))),
        "baseDN" -> trim(label("Base DN", text(required))),
        "userNameAttribute" -> trim(label("User name attribute", text(required))),
        "additionalFilterCondition" -> trim(label("Additional filter condition", optional(text()))),
        "fullNameAttribute" -> trim(label("Full name attribute", optional(text()))),
        "mailAttribute" -> trim(label("Mail address attribute", optional(text()))),
        "tls" -> trim(label("Enable TLS", optional(boolean()))),
        "ssl" -> trim(label("Enable SSL", optional(boolean()))),
        "keystore" -> trim(label("Keystore", optional(text())))
      )(Ldap.apply)
    ),
    "oidcAuthentication" -> trim(label("OIDC", boolean())),
    "oidc" -> optionalIfNotChecked(
      "oidcAuthentication",
      mapping(
        "issuer" -> trim(label("Issuer", text(required))),
        "clientID" -> trim(label("Client ID", text(required))),
        "clientSecret" -> trim(label("Client secret", text(required))),
        "jwsAlgorithm" -> trim(label("Signature algorithm", optional(text())))
      )(OIDC.apply)
    ),
    "skinName" -> trim(label("AdminLTE skin name", text(required))),
    "userDefinedCss" -> trim(label("User-defined CSS", optional(text()))),
    "showMailAddress" -> trim(label("Show mail address", boolean())),
    "webhook" -> mapping(
      "blockPrivateAddress" -> trim(label("Block private address", boolean())),
      "whitelist" -> trim(label("Whitelist", multiLineText()))
    )(WebHook.apply),
    "upload" -> mapping(
      "maxFileSize" -> trim(label("Max file size", long(required))),
      "timeout" -> trim(label("Timeout", long(required))),
      "largeMaxFileSize" -> trim(label("Max file size for large file", long(required))),
      "largeTimeout" -> trim(label("Timeout for large file", long(required)))
    )(Upload.apply),
    "repositoryViewer" -> mapping(
      "maxFiles" -> trim(label("Max files", number(required)))
    )(RepositoryViewerSettings.apply)
  )(SystemSettings.apply).verifying { settings =>
    Vector(
      if (settings.ssh.enabled && settings.baseUrl.isEmpty) {
        Some("baseUrl" -> "Base URL is required if SSH access is enabled.")
      } else None,
      if (settings.ssh.enabled && settings.ssh.sshHost.isEmpty) {
        Some("sshHost" -> "SSH host is required if SSH access is enabled.")
      } else None
    ).flatten
  }

  private val sendMailForm = mapping(
    "smtp" -> mapping(
      "host" -> trim(label("SMTP Host", text(required))),
      "port" -> trim(label("SMTP Port", optional(number()))),
      "user" -> trim(label("SMTP User", optional(text()))),
      "password" -> trim(label("SMTP Password", optional(text()))),
      "ssl" -> trim(label("Enable SSL", optional(boolean()))),
      "starttls" -> trim(label("Enable STARTTLS", optional(boolean()))),
      "fromAddress" -> trim(label("FROM Address", optional(text()))),
      "fromName" -> trim(label("FROM Name", optional(text())))
    )(Smtp.apply),
    "testAddress" -> trim(label("", text(required)))
  )(SendMailForm.apply)

  case class SendMailForm(smtp: Smtp, testAddress: String)

  case class DataExportForm(tableNames: List[String])

  case class NewUserForm(
    userName: String,
    password: String,
    fullName: String,
    mailAddress: String,
    extraMailAddresses: List[String],
    isAdmin: Boolean,
    description: Option[String],
    url: Option[String],
    fileId: Option[String]
  )

  case class EditUserForm(
    userName: String,
    password: Option[String],
    fullName: String,
    mailAddress: String,
    extraMailAddresses: List[String],
    isAdmin: Boolean,
    description: Option[String],
    url: Option[String],
    fileId: Option[String],
    clearImage: Boolean,
    isRemoved: Boolean
  )

  case class NewGroupForm(
    groupName: String,
    description: Option[String],
    url: Option[String],
    fileId: Option[String],
    members: String
  )

  case class EditGroupForm(
    groupName: String,
    description: Option[String],
    url: Option[String],
    fileId: Option[String],
    members: String,
    clearImage: Boolean,
    isRemoved: Boolean
  )

  val newUserForm = mapping(
    "userName" -> trim(label("Username", text(required, maxlength(100), identifier, uniqueUserName, reservedNames))),
    "password" -> trim(label("Password", text(required, maxlength(20)))),
    "fullName" -> trim(label("Full Name", text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address", text(required, maxlength(100), uniqueMailAddress()))),
    "extraMailAddresses" -> list(
      trim(label("Additional Mail Address", text(maxlength(100), uniqueExtraMailAddress("userName"))))
    ),
    "isAdmin" -> trim(label("User Type", boolean())),
    "description" -> trim(label("bio", optional(text()))),
    "url" -> trim(label("URL", optional(text(maxlength(200))))),
    "fileId" -> trim(label("File ID", optional(text())))
  )(NewUserForm.apply)

  val editUserForm = mapping(
    "userName" -> trim(label("Username", text(required, maxlength(100), identifier))),
    "password" -> trim(label("Password", optional(text(maxlength(20))))),
    "fullName" -> trim(label("Full Name", text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address", text(required, maxlength(100), uniqueMailAddress("userName")))),
    "extraMailAddresses" -> list(
      trim(label("Additional Mail Address", text(maxlength(100), uniqueExtraMailAddress("userName"))))
    ),
    "isAdmin" -> trim(label("User Type", boolean())),
    "description" -> trim(label("bio", optional(text()))),
    "url" -> trim(label("URL", optional(text(maxlength(200))))),
    "fileId" -> trim(label("File ID", optional(text()))),
    "clearImage" -> trim(label("Clear image", boolean())),
    "removed" -> trim(label("Disable", boolean(disableByNotYourself("userName"))))
  )(EditUserForm.apply)

  val newGroupForm = mapping(
    "groupName" -> trim(label("Group name", text(required, maxlength(100), identifier, uniqueUserName, reservedNames))),
    "description" -> trim(label("Group description", optional(text()))),
    "url" -> trim(label("URL", optional(text(maxlength(200))))),
    "fileId" -> trim(label("File ID", optional(text()))),
    "members" -> trim(label("Members", text(required, members)))
  )(NewGroupForm.apply)

  val editGroupForm = mapping(
    "groupName" -> trim(label("Group name", text(required, maxlength(100), identifier))),
    "description" -> trim(label("Group description", optional(text()))),
    "url" -> trim(label("URL", optional(text(maxlength(200))))),
    "fileId" -> trim(label("File ID", optional(text()))),
    "members" -> trim(label("Members", text(required, members))),
    "clearImage" -> trim(label("Clear image", boolean())),
    "removed" -> trim(label("Disable", boolean()))
  )(EditGroupForm.apply)

  get("/admin/dbviewer")(adminOnly {
    val conn = request2Session(request).conn
    val meta = conn.getMetaData
    val tables = ListBuffer[Table]()
    Using.resource(meta.getTables(null, "%", "%", Array("TABLE", "VIEW"))) {
      rs =>
        while (rs.next()) {
          val tableName = rs.getString("TABLE_NAME")

          val pkColumns = ListBuffer[String]()
          Using.resource(meta.getPrimaryKeys(null, null, tableName)) { rs =>
            while (rs.next()) {
              pkColumns += rs.getString("COLUMN_NAME").toUpperCase
            }
          }

          val columns = ListBuffer[Column]()
          Using.resource(meta.getColumns(null, "%", tableName, "%")) { rs =>
            while (rs.next()) {
              val columnName = rs.getString("COLUMN_NAME").toUpperCase
              columns += Column(columnName, pkColumns.contains(columnName))
            }
          }

          tables += Table(tableName.toUpperCase, columns.toSeq)
        }
    }
    html.dbviewer(tables.toSeq)
  })

  post("/admin/dbviewer/_query")(adminOnly {
    contentType = formats("json")
    params.get("query").collectFirst {
      case query if query.trim.nonEmpty =>
        val trimmedQuery = query.trim
        if (trimmedQuery.nonEmpty) {
          try {
            val conn = request2Session(request).conn
            Using.resource(conn.prepareStatement(query)) {
              stmt =>
                if (trimmedQuery.toUpperCase.startsWith("SELECT")) {
                  Using.resource(stmt.executeQuery()) {
                    rs =>
                      val meta = rs.getMetaData
                      val columns = for (i <- 1 to meta.getColumnCount) yield {
                        meta.getColumnName(i)
                      }
                      val result = ListBuffer[Map[String, String]]()
                      while (rs.next()) {
                        val row = columns.map { columnName =>
                          columnName -> Option(rs.getObject(columnName)).map(_.toString).getOrElse("<NULL>")
                        }.toMap
                        result += row
                      }
                      Ok(Serialization.write(Map("type" -> "query", "columns" -> columns, "rows" -> result)))
                  }
                } else {
                  val rows = stmt.executeUpdate()
                  Ok(Serialization.write(Map("type" -> "update", "rows" -> rows)))
                }
            }
          } catch {
            case e: Exception =>
              Ok(Serialization.write(Map("type" -> "error", "message" -> e.toString)))
          }
        }
    } getOrElse Ok(Serialization.write(Map("type" -> "error", "message" -> "query is empty")))
  })

  get("/admin/system")(adminOnly {
    html.settings(flash.get("info"))
  })

  post("/admin/system", form)(adminOnly { form =>
    saveSystemSettings(form)

    if (form.sshAddress != context.settings.sshAddress) {
      SshServer.stop()
      for {
        sshAddress <- form.sshAddress
        baseUrl <- form.baseUrl
      } SshServer.start(sshAddress, baseUrl)
    }

    flash.update("info", "System settings has been updated.")
    redirect("/admin/system")
  })

  post("/admin/system/sendmail", sendMailForm)(adminOnly { form =>
    try {
      new Mailer(context.settings.copy(smtp = Some(form.smtp), notification = true)).send(
        to = form.testAddress,
        subject = "Test message from GitBucket",
        textMsg = "This is a test message from GitBucket.",
        htmlMsg = None,
        loginAccount = context.loginAccount
      )

      "Test mail has been sent to: " + form.testAddress

    } catch {
      case e: EmailException => s"[Error] ${e.getCause}"
      case e: Exception      => s"[Error] ${e.toString}"
    }
  })

  get("/admin/plugins")(adminOnly {
    html.plugins(PluginRegistry().getPlugins(), flash.get("info"))
  })

  post("/admin/plugins/_reload")(adminOnly {
    PluginRegistry.reload(request.getServletContext(), loadSystemSettings(), request2Session(request).conn)
    flash.update("info", "All plugins were reloaded.")
    redirect("/admin/plugins")
  })

  post("/admin/plugins/:pluginId/_uninstall")(adminOnly {
    val pluginId = params("pluginId")

    if (PluginRegistry().getPlugins().exists(_.pluginId == pluginId)) {
      PluginRegistry
        .uninstall(pluginId, request.getServletContext, loadSystemSettings(), request2Session(request).conn)
      flash.update("info", s"${pluginId} was uninstalled.")
    }

    redirect("/admin/plugins")
  })

  get("/admin/users")(adminOnly {
    val includeRemoved = params.get("includeRemoved").map(_.toBoolean).getOrElse(false)
    val includeGroups = params.get("includeGroups").map(_.toBoolean).getOrElse(false)
    val users = getAllUsers(includeRemoved, includeGroups)
    val members = users.collect {
      case account if (account.isGroupAccount) =>
        account.userName -> getGroupMembers(account.userName).map(_.userName)
    }.toMap

    html.userlist(users, members, includeRemoved, includeGroups)
  })

  get("/admin/users/_newuser")(adminOnly {
    html.user(None, Nil)
  })

  post("/admin/users/_newuser", newUserForm)(adminOnly { form =>
    createAccount(
      form.userName,
      pbkdf2_sha256(form.password),
      form.fullName,
      form.mailAddress,
      form.isAdmin,
      form.description,
      form.url
    )
    updateImage(form.userName, form.fileId, false)
    updateAccountExtraMailAddresses(form.userName, form.extraMailAddresses.filter(_ != ""))
    redirect("/admin/users")
  })

  get("/admin/users/:userName/_edituser")(adminOnly {
    val userName = params("userName")
    val extraMails = getAccountExtraMailAddresses(userName)
    html.user(getAccountByUserName(userName, true), extraMails, flash.get("error"))
  })

  post("/admin/users/:name/_edituser", editUserForm)(adminOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName, true).map {
      account =>
        if (account.isAdmin && (form.isRemoved || !form.isAdmin) && isLastAdministrator(account)) {
          flash.update("error", "Account can't be turned off because this is last one administrator.")
          redirect(s"/admin/users/${userName}/_edituser")
        } else {
          if (form.isRemoved) {
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

          updateAccount(
            account.copy(
              password = form.password.map(pbkdf2_sha256).getOrElse(account.password),
              fullName = form.fullName,
              mailAddress = form.mailAddress,
              isAdmin = form.isAdmin,
              description = form.description,
              url = form.url,
              isRemoved = form.isRemoved
            )
          )

          updateImage(userName, form.fileId, form.clearImage)
          updateAccountExtraMailAddresses(userName, form.extraMailAddresses.filter(_ != ""))

          // call hooks
          if (form.isRemoved) PluginRegistry().getAccountHooks.foreach(_.deleted(userName))

          redirect("/admin/users")
        }
    } getOrElse NotFound()
  })

  get("/admin/users/_newgroup")(adminOnly {
    html.usergroup(None, Nil)
  })

  post("/admin/users/_newgroup", newGroupForm)(adminOnly { form =>
    createGroup(form.groupName, form.description, form.url)
    updateGroupMembers(
      form.groupName,
      form.members
        .split(",")
        .map {
          _.split(":") match {
            case Array(userName, isManager) => (userName, isManager.toBoolean)
          }
        }
        .toList
    )
    updateImage(form.groupName, form.fileId, false)
    redirect("/admin/users")
  })

  get("/admin/users/:groupName/_editgroup")(adminOnly {
    defining(params("groupName")) { groupName =>
      html.usergroup(getAccountByUserName(groupName, true), getGroupMembers(groupName))
    }
  })

  post("/admin/users/:groupName/_editgroup", editGroupForm)(adminOnly { form =>
    defining(
      params("groupName"),
      form.members
        .split(",")
        .map {
          _.split(":") match {
            case Array(userName, isManager) => (userName, isManager.toBoolean)
          }
        }
        .toList
    ) {
      case (groupName, members) =>
        getAccountByUserName(groupName, true).map {
          account =>
            updateGroup(groupName, form.description, form.url, form.isRemoved)

            if (form.isRemoved) {
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

    Using.resource(new FileInputStream(file)) { in =>
      IOUtils.copy(in, response.outputStream)
    }

    ()
  })

  private def multiLineText(constraints: Constraint*): SingleValueType[Seq[String]] =
    new SingleValueType[Seq[String]](constraints: _*) {
      def convert(value: String, messages: Messages): Seq[String] = {
        if (value == null) {
          Nil
        } else {
          value.split("\n").toIndexedSeq.map(_.trim)
        }
      }
    }

  private def members: Constraint =
    new Constraint() {
      override def validate(name: String, value: String, messages: Messages): Option[String] = {
        if (value.split(",").exists {
              _.split(":") match { case Array(userName, isManager) => isManager.toBoolean }
            }) None
        else Some("Must select one manager at least.")
      }
    }

  protected def disableByNotYourself(paramName: String): Constraint =
    new Constraint() {
      override def validate(name: String, value: String, messages: Messages): Option[String] = {
        params.get(paramName).flatMap { userName =>
          if (userName == context.loginAccount.get.userName && params.get("removed") == Some("true"))
            Some("You can't disable your account yourself")
          else
            None
        }
      }
    }

}
