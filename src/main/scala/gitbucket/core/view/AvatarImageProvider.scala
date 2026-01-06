package gitbucket.core.view

import gitbucket.core.controller.Context
import gitbucket.core.service.RequestCache
import gitbucket.core.util.StringUtil
import play.twirl.api.Html

trait AvatarImageProvider { self: RequestCache =>

  /**
   * Returns &lt;img&gt; which displays the avatar icon.
   * Looks up Gravatar if avatar icon has not been configured in user settings.
   */
  protected def getAvatarImageHtml(userName: String, size: Int, mailAddress: String = "", tooltip: Boolean = false)(
    implicit context: Context
  ): Html = {

    val src = if (mailAddress.isEmpty) {
      // by user name
      getAccountByUserNameFromCache(userName).map { account =>
        if (account.image.isEmpty && context.settings.basicBehavior.gravatar) {
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(
              account.mailAddress.toLowerCase
            )}?s=${size}&d=retro&r=g"""
        } else {
          s"""${context.path}/${account.userName}/_avatar?${helpers.hashDate(account.updatedDate)}"""
        }
      } getOrElse {
        s"""${context.path}/_unknown/_avatar"""
      }
    } else {
      // by mail address
      getAccountByMailAddressFromCache(mailAddress).map { account =>
        if (account.image.isEmpty && context.settings.basicBehavior.gravatar) {
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(
              account.mailAddress.toLowerCase
            )}?s=${size}&d=retro&r=g"""
        } else {
          s"""${context.path}/${account.userName}/_avatar?${helpers.hashDate(account.updatedDate)}"""
        }
      } getOrElse {
        if (context.settings.basicBehavior.gravatar) {
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(mailAddress.toLowerCase)}?s=${size}&d=retro&r=g"""
        } else {
          s"""${context.path}/_unknown/_avatar"""
        }
      }
    }

    val displayName = if (!context.settings.showFullName) {
      s"@$userName"
    } else {
      if (mailAddress.isEmpty) {
        getAccountByUserNameFromCache(userName).map(_.fullName).getOrElse(s"@$userName")
      } else {
        getAccountByMailAddressFromCache(mailAddress).map(_.fullName).getOrElse(s"@$userName")
      }
    }

    if (tooltip) {
      Html(
        s"""<img src="${src}" class="${if (size > 20) { "avatar" }
          else { "avatar-mini" }}" style="width: ${size}px; height: ${size}px;"
           |     alt="${StringUtil.escapeHtml(displayName)}"
           |     data-toggle="tooltip" title="${StringUtil.escapeHtml(displayName)}" />""".stripMargin
      )
    } else {
      Html(
        s"""<img src="${src}" class="${if (size > 20) { "avatar" }
          else { "avatar-mini" }}" style="width: ${size}px; height: ${size}px;"
           |     alt="${StringUtil.escapeHtml(displayName)}" />""".stripMargin
      )
    }
  }

}
