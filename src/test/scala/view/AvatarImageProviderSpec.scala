package view

import java.util.Date

import org.specs2.mutable._
import service.RequestCache
import model.Account
import service.SystemSettingsService.SystemSettings
import twirl.api.Html

class AvatarImageProviderSpec extends Specification {

  "getAvatarImageHtml" should {
    "show Gravatar image for no image account if gravatar integration is enabled" in {
      implicit val context = app.Context(createSystemSettings(true), None, null)
      val provider = new AvatarImageProviderImpl(Some(createAccount(None)))

      provider.toHtml("user", 20).toString mustEqual
        "<img src=\"https://www.gravatar.com/avatar/d41d8cd98f00b204e9800998ecf8427e?s=20&d=retro&r=g\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show uploaded image even if gravatar integration is enabled" in {
      implicit val context = app.Context(createSystemSettings(true), None, null)
      val provider = new AvatarImageProviderImpl(Some(createAccount(Some("icon.png"))))

      provider.toHtml("user", 20).toString mustEqual
        "<img src=\"/user/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show local image for no image account if gravatar integration is disabled" in {
      implicit val context = app.Context(createSystemSettings(false), None, null)
      val provider = new AvatarImageProviderImpl(Some(createAccount(None)))

      provider.toHtml("user", 20).toString mustEqual
        "<img src=\"/user/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show Gravatar image for specified mail address if gravatar integration is enabled" in {
      implicit val context = app.Context(createSystemSettings(true), None, null)
      val provider = new AvatarImageProviderImpl(None)

      provider.toHtml("user", 20, "hoge@hoge.com").toString mustEqual
        "<img src=\"https://www.gravatar.com/avatar/4712f9b0e63f56ad952ad387eaa23b9c?s=20&d=retro&r=g\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show unknown image for unknown user if gravatar integration is enabled" in {
      implicit val context = app.Context(createSystemSettings(true), None, null)
      val provider = new AvatarImageProviderImpl(None)

      provider.toHtml("user", 20).toString mustEqual
        "<img src=\"/_unknown/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "show unknown image for specified mail address if gravatar integration is disabled" in {
      implicit val context = app.Context(createSystemSettings(false), None, null)
      val provider = new AvatarImageProviderImpl(None)

      provider.toHtml("user", 20, "hoge@hoge.com").toString mustEqual
        "<img src=\"/_unknown/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" />"
    }

    "add tooltip if it's enabled" in {
      implicit val context = app.Context(createSystemSettings(false), None, null)
      val provider = new AvatarImageProviderImpl(None)

      provider.toHtml("user", 20, "hoge@hoge.com", true).toString mustEqual
        "<img src=\"/_unknown/_avatar\" class=\"avatar\" style=\"width: 20px; height: 20px;\" data-toggle=\"tooltip\" title=\"user\"/>"
    }
  }

  private def createAccount(image: Option[String]) =
    Account(
      userName       = "user",
      fullName       = "user@localhost",
      mailAddress    = "",
      password       = "",
      isAdmin        = false,
      url            = None,
      registeredDate = new Date(),
      updatedDate    = new Date(),
      lastLoginDate  = None,
      image          = image,
      isGroupAccount = false,
      isRemoved      = false)

  private def createSystemSettings(useGravatar: Boolean) =
    SystemSettings(
      baseUrl                  = Some(""),
      allowAccountRegistration = false,
      gravatar                 = useGravatar,
      notification             = false,
      ssh                      = false,
      sshPort                  = None,
      smtp                     = None,
      ldapAuthentication       = false,
      ldap                     = None)

  /**
   * Adapter to test AvatarImageProviderImpl.
   */
  class AvatarImageProviderImpl(account: Option[Account]) extends AvatarImageProvider with RequestCache {

    def toHtml(userName: String, size: Int,  mailAddress: String = "", tooltip: Boolean = false)
              (implicit context: app.Context): Html = getAvatarImageHtml(userName, size, mailAddress, tooltip)

    override def getAccountByMailAddress(mailAddress: String)(implicit context: app.Context): Option[Account] = account
    override def getAccountByUserName(userName: String)(implicit context: app.Context): Option[Account] = account
  }

}
