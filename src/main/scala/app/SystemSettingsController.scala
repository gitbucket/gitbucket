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

  private case class SystemSettingsForm(allowAccountRegistration: Boolean)

  private val form = mapping(
    "allowAccountRegistration" -> trim(label("Account registration", boolean()))
  )(SystemSettingsForm.apply)


  get("/admin/system")(adminOnly {
    admin.html.system(loadSystemSettings(), flash.get("info"))
  })

  post("/admin/system", form)(adminOnly { form =>
    saveSystemSettings(SystemSettings(form.allowAccountRegistration))
    flash += "info" -> "Settings updated."
    redirect("/admin/system")
  })

}
