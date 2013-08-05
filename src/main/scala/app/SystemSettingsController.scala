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

  private case class SystemSettingsForm(
    allowAccountRegistration: Boolean,
    gravatar: Boolean
  )

  private val form = mapping(
    "allowAccountRegistration" -> trim(label("Account registration", boolean())),
    "gravatar"                 -> trim(label("Gravatar", boolean()))
  )(SystemSettingsForm.apply)


  get("/admin/system")(adminOnly {
    admin.html.system(loadSystemSettings(), flash.get("info"))
  })

  post("/admin/system", form)(adminOnly { form =>
    saveSystemSettings(SystemSettings(
      form.allowAccountRegistration,
      form.gravatar
    ))
    flash += "info" -> "System settings has been updated."
    redirect("/admin/system")
  })

}
