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
  protected def getAvatarImageHtml(userName: String, size: Int,
      mailAddress: String = "", tooltip: Boolean = false)(implicit context: Context): Html = {

    val src = if(mailAddress.isEmpty){
      // by user name
      getAccountByUserName(userName).map { account =>
        if(account.image.isEmpty && context.settings.gravatar){
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(account.mailAddress.toLowerCase)}?s=${size}&d=retro&r=g"""
        } else {
          s"""${context.path}/${account.userName}/_avatar"""
        }
      } getOrElse {
        s"""${context.path}/_unknown/_avatar"""
      }
    } else {
      // by mail address
      getAccountByMailAddress(mailAddress).map { account =>
        if(account.image.isEmpty && context.settings.gravatar){
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(account.mailAddress.toLowerCase)}?s=${size}&d=retro&r=g"""
        } else {
          s"""${context.path}/${account.userName}/_avatar"""
        }
      } getOrElse {
        if(context.settings.gravatar){
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(mailAddress.toLowerCase)}?s=${size}&d=retro&r=g"""
        } else {
          s"""${context.path}/_unknown/_avatar"""
        }
      }
    }

    if(tooltip){
      Html(s"""<img src="${src}" class="${if(size > 20){"avatar"} else {"avatar-mini"}}" style="width: ${size}px; height: ${size}px;" data-toggle="tooltip" title="${userName}"/>""")
    } else {
      Html(s"""<img src="${src}" class="${if(size > 20){"avatar"} else {"avatar-mini"}}" style="width: ${size}px; height: ${size}px;" />""")
    }
  }

}