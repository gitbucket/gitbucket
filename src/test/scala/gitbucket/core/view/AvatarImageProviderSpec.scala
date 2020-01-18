package gitbucket.core.view

import java.text.SimpleDateFormat
import java.util.Date

import javax.servlet.http.{HttpServletRequest, HttpSession}
import gitbucket.core.controller.Context
import gitbucket.core.model.Account
import gitbucket.core.service.RequestCache
import gitbucket.core.service.SystemSettingsService.{Ssh, SystemSettings, WebHook, Upload}
import org.mockito.Mockito._
import org.scalatest.FunSpec
import org.scalatestplus.mockito.MockitoSugar
import play.twirl.api.Html

class AvatarImageProviderSpec extends FunSpec with MockitoSugar {

  val request = mock[HttpServletRequest]
  val session = mock[HttpSession]
  when(request.getRequestURL).thenReturn(new StringBuffer("http://localhost:8080/path.html"))
  when(request.getRequestURI).thenReturn("/path.html")
  when(request.getContextPath).thenReturn("")
  when(request.getSession).thenReturn(session)

  describe("getAvatarImageHtml") {
    it("should show Gravatar image for no image account if gravatar integration is enabled") {
      implicit val context = Context(createSystemSettings(true), None, request)
      val provider = new AvatarImageProviderImpl(Some(createAccount(None)))

      assert(
        provider.toHtml("user", 32).toString ==
          "<img src=\"https://www.gravatar.com/avatar/d41d8cd98f00b204e9800998ecf8427e?s=32&d=retro&r=g\" class=\"avatar\" style=\"width: 32px; height: 32px;\" />"
      )
    }

    it("should show uploaded image even if gravatar integration is enabled") {
      implicit val context = Context(createSystemSettings(true), None, request)
      val account = createAccount((Some("icon.png")))
      val date = new SimpleDateFormat("yyyyMMddHHmmss").format(account.updatedDate)
      val provider = new AvatarImageProviderImpl(Some(account))

      assert(
        provider.toHtml("user", 32).toString ==
          s"""<img src="/user/_avatar?${date}" class="avatar" style="width: 32px; height: 32px;" />"""
      )
    }

    it("should show local image for no image account if gravatar integration is disabled") {
      implicit val context = Context(createSystemSettings(false), None, request)
      val account = createAccount(None)
      val date = new SimpleDateFormat("yyyyMMddHHmmss").format(account.updatedDate)
      val provider = new AvatarImageProviderImpl(Some(account))

      assert(
        provider.toHtml("user", 32).toString ==
          s"""<img src="/user/_avatar?${date}" class="avatar" style="width: 32px; height: 32px;" />"""
      )
    }

    it("should show Gravatar image for specified mail address if gravatar integration is enabled") {
      implicit val context = Context(createSystemSettings(true), None, request)
      val provider = new AvatarImageProviderImpl(None)

      assert(
        provider.toHtml("user", 20, "hoge@hoge.com").toString ==
          "<img src=\"https://www.gravatar.com/avatar/4712f9b0e63f56ad952ad387eaa23b9c?s=20&d=retro&r=g\" class=\"avatar-mini\" style=\"width: 20px; height: 20px;\" />"
      )
    }

    it("should show unknown image for unknown user if gravatar integration is enabled") {
      implicit val context = Context(createSystemSettings(true), None, request)
      val provider = new AvatarImageProviderImpl(None)

      assert(
        provider.toHtml("user", 20).toString ==
          "<img src=\"/_unknown/_avatar\" class=\"avatar-mini\" style=\"width: 20px; height: 20px;\" />"
      )
    }

    it("should show unknown image for specified mail address if gravatar integration is disabled") {
      implicit val context = Context(createSystemSettings(false), None, request)
      val provider = new AvatarImageProviderImpl(None)

      assert(
        provider.toHtml("user", 20, "hoge@hoge.com").toString ==
          "<img src=\"/_unknown/_avatar\" class=\"avatar-mini\" style=\"width: 20px; height: 20px;\" />"
      )
    }

    it("should add tooltip if it's enabled") {
      implicit val context = Context(createSystemSettings(false), None, request)
      val provider = new AvatarImageProviderImpl(None)

      assert(
        provider.toHtml("user", 20, "hoge@hoge.com", true).toString ==
          "<img src=\"/_unknown/_avatar\" class=\"avatar-mini\" style=\"width: 20px; height: 20px;\" data-toggle=\"tooltip\" title=\"user\"/>"
      )
    }
  }

  private def createAccount(image: Option[String]) =
    Account(
      userName = "user",
      fullName = "user@localhost",
      mailAddress = "",
      password = "",
      isAdmin = false,
      url = None,
      registeredDate = new Date(),
      updatedDate = new Date(),
      lastLoginDate = None,
      image = image,
      isGroupAccount = false,
      isRemoved = false,
      description = None
    )

  private def createSystemSettings(useGravatar: Boolean) =
    SystemSettings(
      baseUrl = None,
      information = None,
      allowAccountRegistration = false,
      allowAnonymousAccess = true,
      isCreateRepoOptionPublic = true,
      gravatar = useGravatar,
      notification = false,
      activityLogLimit = None,
      limitVisibleRepositories = false,
      ssh = Ssh(
        enabled = false,
        sshHost = None,
        sshPort = None
      ),
      useSMTP = false,
      smtp = None,
      ldapAuthentication = false,
      ldap = None,
      oidcAuthentication = false,
      oidc = None,
      skinName = "skin-blue",
      showMailAddress = false,
      webHook = WebHook(
        blockPrivateAddress = false,
        whitelist = Nil
      ),
      upload = Upload(
        maxFileSize = 3 * 1024 * 1024,
        timeout = 30 * 10000,
        largeMaxFileSize = 3 * 1024 * 1024,
        largeTimeout = 30 * 10000
      )
    )

  /**
   * Adapter to test AvatarImageProviderImpl.
   */
  class AvatarImageProviderImpl(account: Option[Account]) extends AvatarImageProvider with RequestCache {

    def toHtml(userName: String, size: Int, mailAddress: String = "", tooltip: Boolean = false)(
      implicit context: Context
    ): Html = getAvatarImageHtml(userName, size, mailAddress, tooltip)

    override def getAccountByMailAddress(mailAddress: String)(implicit context: Context): Option[Account] = account
    override def getAccountByUserName(userName: String)(implicit context: Context): Option[Account] = account
  }

}
