package view

import java.util.Date

import org.specs2.mutable._
import service.RequestCache
import model.Account
import service.SystemSettingsService.SystemSettings
import twirl.api.Html

class AvatarImageProviderSpec extends Specification {

  implicit val context = app.Context("", None, "", null)

  "getAvatarImageHtml" should {
    "show Gravatar image for no image account if gravatar integration is enabled" in {
      val provider = new AvatarImageProviderImpl(Some(createAccount(None)), createSystemSettings(true))

      provider.toHtml("user", 20).toString mustEqual
        "<img src=\"http://www.gravatar.com/avatar/d41d8cd98f00b204e9800998ecf8427e?s=20\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show uploaded image even if gravatar integration is enabled" in {
      val provider = new AvatarImageProviderImpl(Some(createAccount(Some("icon.png"))), createSystemSettings(true))

      provider.toHtml("user", 20).toString mustEqual
        "<img src=\"/user/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show local image for no image account if gravatar integration is disabled" in {
      val provider = new AvatarImageProviderImpl(Some(createAccount(None)), createSystemSettings(false))

      provider.toHtml("user", 20).toString mustEqual
        "<img src=\"/user/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show Gravatar image for specified mail address if gravatar integration is enabled" in {
      val provider = new AvatarImageProviderImpl(None, createSystemSettings(true))

      provider.toHtml("user", 20, "hoge@hoge.com").toString mustEqual
        "<img src=\"http://www.gravatar.com/avatar/4712f9b0e63f56ad952ad387eaa23b9c?s=20\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show local image for unknown user if gravatar integration is enabled" in {
      val provider = new AvatarImageProviderImpl(None, createSystemSettings(true))

      provider.toHtml("user", 20).toString mustEqual
        "<img src=\"/user/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show local image for specified mail address if gravatar integration is disabled" in {
      val provider = new AvatarImageProviderImpl(None, createSystemSettings(false))

      provider.toHtml("user", 20, "hoge@hoge.com").toString mustEqual
        "<img src=\"/user/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "add tooltip if it's enabled" in {
      val provider = new AvatarImageProviderImpl(None, createSystemSettings(false))

      provider.toHtml("user", 20, "hoge@hoge.com", true).toString mustEqual
        "<img src=\"/user/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" data-toggle=\"tooltip\" title=\"user\"/>"
    }
  }

  private def createAccount(image: Option[String]) =
    Account(
      userName       = "",
      mailAddress    = "",
      password       = "",
      isAdmin        = false,
      url            = None,
      registeredDate = new Date(),
      updatedDate    = new Date(),
      lastLoginDate  = None,
      image          = image,
      isGroupAccount = false)

  private def createSystemSettings(useGravatar: Boolean) =
    SystemSettings(
      allowAccountRegistration = false,
      gravatar                 = useGravatar,
      notification             = false,
      smtp                     = None,
      ldapAuthentication       = false,
      ldap                     = None)

  /**
   * Adapter to test AvatarImageProviderImpl.
   */
  class AvatarImageProviderImpl(account: Option[Account], settings: SystemSettings)
      extends AvatarImageProvider with RequestCache {

    def toHtml(userName: String, size: Int,  mailAddress: String = "", tooltip: Boolean = false)
              (implicit context: app.Context): Html = getAvatarImageHtml(userName, size, mailAddress, tooltip)

    override def getAccountByUserName(userName: String)(implicit context: app.Context): Option[Account] = account
    override def getSystemSettings()(implicit context: app.Context): SystemSettings = settings
  }

}
