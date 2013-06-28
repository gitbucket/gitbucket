package app

import service.{AccountService, SystemSettingsService}
import SystemSettingsService._
import util.AdminOnlyAuthenticator
import jp.sf.amateras.scalatra.forms._

class SystemSettingsController extends SystemSettingsControllerBase
  with SystemSettingsService with AccountService with AdminOnlyAuthenticator

trait SystemSettingsControllerBase extends ControllerBase {
  self: SystemSettingsService with AccountService with AdminOnlyAuthenticator =>

  private case class SystemSettingsForm(allowAccountRegistration: Boolean)

  private val form = mapping(
    "allowAccountRegistration" -> trim(label("Account registration", boolean()))
  )(SystemSettingsForm.apply)


  get("/admin/system")(adminOnly {
    admin.html.system(loadSystemSettings())
  })

  post("/admin/system", form)(adminOnly { form =>
    saveSystemSettings(SystemSettings(form.allowAccountRegistration))
    redirect("/admin/system")
  })

}
