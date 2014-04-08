package app

import service.{AccountService, SystemSettingsService}
import SystemSettingsService._
import util.AdminAuthenticator
import jp.sf.amateras.scalatra.forms._
import ssh.SshServer

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
        "fullNameAttribute"        -> trim(label("Full name attribute", optional(text()))),
        "mailAttribute"            -> trim(label("Mail address attribute", text(required))),
        "tls"                      -> trim(label("Enable TLS", optional(boolean()))),
        "keystore"                 -> trim(label("Keystore", optional(text()))),
        "httpSsoHeader"            -> trim(label("HTTP Single Sign-On header", optional(text())))
    )(Ldap.apply))
  )(SystemSettings.apply).verifying { settings =>
    if(settings.ssh && settings.baseUrl.isEmpty){
      Seq("baseUrl" -> "Base URL is required if SSH access is enabled.")
    } else Nil
  }


  get("/admin/system")(adminOnly {
    admin.html.system(flash.get("info"))
  })

  post("/admin/system", form)(adminOnly { form =>
    saveSystemSettings(form)

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

}
