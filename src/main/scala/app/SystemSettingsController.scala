package app

import service.{AccountService, SystemSettingsService}
import SystemSettingsService._
import util.AdminAuthenticator
import jp.sf.amateras.scalatra.forms._
import org.scalatra.FlashMapSupport

class SystemSettingsController extends SystemSettingsControllerBase
  with SystemSettingsService with AccountService with AdminAuthenticator

trait SystemSettingsControllerBase extends ControllerBase with FlashMapSupport {
  self: SystemSettingsService with AccountService with AdminAuthenticator =>

  private val form = mapping(
    "allowAccountRegistration" -> trim(label("Account registration", boolean())),
    "gravatar"                 -> trim(label("Gravatar", boolean())),
    "notification"             -> trim(label("Notification", boolean())),
    "smtp"                     -> mapping(
        "host"     -> trim(label("SMTP Host", text(new Constraint(){
          def validate(name: String, value: String): Option[String] =
            if(params.get("notification").exists(_ == "on")) required.validate(name, value) else None
        }))),
        "port"     -> trim(label("SMTP Port", optional(number()))),
        "user"     -> trim(label("SMTP User", optional(text()))),
        "password" -> trim(label("SMTP Password", optional(text()))),
        "ssl"      -> trim(label("Enable SSL", optional(boolean())))
    )(Smtp.apply)
  )(SystemSettings.apply)


  get("/admin/system")(adminOnly {
    admin.html.system(loadSystemSettings(), flash.get("info"))
  })

  post("/admin/system", form)(adminOnly { form =>
    saveSystemSettings(form)
    flash += "info" -> "System settings has been updated."
    redirect("/admin/system")
  })

}
