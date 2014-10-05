package app

import service.{AccountService, SystemSettingsService}
import SystemSettingsService._
import util.AdminAuthenticator
import util.Directory._
import util.ControlUtil._
import jp.sf.amateras.scalatra.forms._
import ssh.SshServer
import org.apache.commons.io.FileUtils
import java.io.FileInputStream
import plugin.{Plugin, PluginSystem}
import org.scalatra.Ok
import util.Implicits._

class SystemSettingsController extends SystemSettingsControllerBase
  with AccountService with AdminAuthenticator

trait SystemSettingsControllerBase extends ControllerBase {
  self: AccountService with AdminAuthenticator =>

  private val form = mapping(
    "baseUrl"                  -> trim(label("Base URL", optional(text()))),
    "allowAccountRegistration" -> trim(label("Account registration", boolean())),
    "gravatar"                 -> trim(label("Gravatar", boolean())),
    "notification"             -> trim(label("Notification", boolean())),
    "ssh"                      -> trim(label("SSH access", boolean())),
    "sshPort"                  -> trim(label("SSH port", optional(number()))),
    "smtp"                     -> optionalIfNotChecked("notification", mapping(
        "host"                     -> trim(label("SMTP Host", text(required))),
        "port"                     -> trim(label("SMTP Port", optional(number()))),
        "user"                     -> trim(label("SMTP User", optional(text()))),
        "password"                 -> trim(label("SMTP Password", optional(text()))),
        "ssl"                      -> trim(label("Enable SSL", optional(boolean()))),
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
        "keystore"                 -> trim(label("Keystore", optional(text())))
    )(Ldap.apply))
  )(SystemSettings.apply).verifying { settings =>
    if(settings.ssh && settings.baseUrl.isEmpty){
      Seq("baseUrl" -> "Base URL is required if SSH access is enabled.")
    } else Nil
  }

  private val pluginForm = mapping(
    "pluginId" -> list(trim(label("", text())))
  )(PluginForm.apply)

  case class PluginForm(pluginIds: List[String])

  get("/admin/system")(adminOnly {
    admin.html.system(flash.get("info"))
  })

  post("/admin/system", form)(adminOnly { form =>
    saveSystemSettings(form)

    if(form.ssh && SshServer.isActive && context.settings.sshPort != form.sshPort){
      SshServer.stop()
    }

    if(form.ssh && !SshServer.isActive && form.baseUrl.isDefined){
      SshServer.start(request.getServletContext,
        form.sshPort.getOrElse(SystemSettingsService.DefaultSshPort),
        form.baseUrl.get)
    } else if(!form.ssh && SshServer.isActive){
      SshServer.stop()
    }

    flash += "info" -> "System settings has been updated."
    redirect("/admin/system")
  })

  get("/admin/plugins")(adminOnly {
    if(enablePluginSystem){
      val installedPlugins = plugin.PluginSystem.plugins
      val updatablePlugins = getAvailablePlugins(installedPlugins).filter(_.status == "updatable")
      admin.plugins.html.installed(installedPlugins, updatablePlugins)
    } else NotFound
  })

  post("/admin/plugins/_update", pluginForm)(adminOnly { form =>
    if(enablePluginSystem){
      deletePlugins(form.pluginIds)
      installPlugins(form.pluginIds)
      redirect("/admin/plugins")
    } else NotFound
  })

  post("/admin/plugins/_delete", pluginForm)(adminOnly { form =>
    if(enablePluginSystem){
      deletePlugins(form.pluginIds)
      redirect("/admin/plugins")
    } else NotFound
  })

  get("/admin/plugins/available")(adminOnly {
    if(enablePluginSystem){
      val installedPlugins = plugin.PluginSystem.plugins
      val availablePlugins = getAvailablePlugins(installedPlugins).filter(_.status == "available")
      admin.plugins.html.available(availablePlugins)
    } else NotFound
  })

  post("/admin/plugins/_install", pluginForm)(adminOnly { form =>
    if(enablePluginSystem){
      installPlugins(form.pluginIds)
      redirect("/admin/plugins")
    } else NotFound
  })

  get("/admin/plugins/console")(adminOnly {
    if(enablePluginSystem){
      admin.plugins.html.console()
    } else NotFound
  })

  post("/admin/plugins/console")(adminOnly {
    if(enablePluginSystem){
      val script = request.getParameter("script")
      val result = plugin.ScalaPlugin.eval(script)
      Ok()
    } else NotFound
  })

  // TODO Move these methods to PluginSystem or Service?
  private def deletePlugins(pluginIds: List[String]): Unit = {
    pluginIds.foreach { pluginId =>
      plugin.PluginSystem.uninstall(pluginId)
      val dir = new java.io.File(PluginHome, pluginId)
      if(dir.exists && dir.isDirectory){
        FileUtils.deleteQuietly(dir)
        PluginSystem.uninstall(pluginId)
      }
    }
  }

  private def installPlugins(pluginIds: List[String]): Unit = {
    val dir = getPluginCacheDir()
    val installedPlugins = plugin.PluginSystem.plugins
    getAvailablePlugins(installedPlugins).filter(x => pluginIds.contains(x.id)).foreach { plugin =>
      val pluginDir = new java.io.File(PluginHome, plugin.id)
      if(pluginDir.exists){
        FileUtils.deleteDirectory(pluginDir)
      }
      FileUtils.copyDirectory(new java.io.File(dir, plugin.repository + "/" + plugin.id), pluginDir)
      PluginSystem.installPlugin(plugin.id)
    }
  }

  private def getAvailablePlugins(installedPlugins: List[Plugin]): List[SystemSettingsControllerBase.AvailablePlugin] = {
    val repositoryRoot = getPluginCacheDir()

    if(repositoryRoot.exists && repositoryRoot.isDirectory){
      PluginSystem.repositories.flatMap { repo =>
        val repoDir = new java.io.File(repositoryRoot, repo.id)
        if(repoDir.exists && repoDir.isDirectory){
          repoDir.listFiles.filter(d => d.isDirectory && !d.getName.startsWith(".")).map { plugin =>
            val propertyFile = new java.io.File(plugin, "plugin.properties")
            val properties = new java.util.Properties()
            if(propertyFile.exists && propertyFile.isFile){
              using(new FileInputStream(propertyFile)){ in =>
                properties.load(in)
              }
            }
            SystemSettingsControllerBase.AvailablePlugin(
              repository  = repo.id,
              id          = properties.getProperty("id"),
              version     = properties.getProperty("version"),
              author      = properties.getProperty("author"),
              url         = properties.getProperty("url"),
              description = properties.getProperty("description"),
              status      = installedPlugins.find(_.id == properties.getProperty("id")) match {
                case Some(x) if(PluginSystem.isUpdatable(x.version, properties.getProperty("version")))=> "updatable"
                case Some(x) => "installed"
                case None    => "available"
              })
          }
        } else Nil
      }
    } else Nil
  }
}

object SystemSettingsControllerBase {
  case class AvailablePlugin(repository: String, id: String, version: String,
                             author: String, url: String, description: String, status: String)
}
