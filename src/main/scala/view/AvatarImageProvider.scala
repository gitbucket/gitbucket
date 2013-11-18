package view

import service.RequestCache
import twirl.api.Html
import util.StringUtil

trait AvatarImageProvider { self: RequestCache =>

  /**
   * Returns &lt;img&gt; which displays the avatar icon.
   * Looks up Gravatar if avatar icon has not been configured in user settings.
   */
  protected def getAvatarImageHtml(userName: String, size: Int,
      mailAddress: String = "", tooltip: Boolean = false)(implicit context: app.Context): Html = {

    val src = if(mailAddress.isEmpty){
      // by user name
      getAccountByUserName(userName).map { account =>
        if(account.image.isEmpty && getSystemSettings().gravatar){
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(account.mailAddress.toLowerCase)}?s=${size}"""
        } else {
          s"""${context.path}/${account.userName}/_avatar"""
        }
      } getOrElse {
        s"""${context.path}/_unknown/_avatar"""
      }
    } else {
      // by mail address
      getAccountByMailAddress(mailAddress).map { account =>
        if(account.image.isEmpty && getSystemSettings().gravatar){
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(account.mailAddress.toLowerCase)}?s=${size}"""
        } else {
          s"""${context.path}/${account.userName}/_avatar"""
        }
      } getOrElse {
        if(getSystemSettings().gravatar){
          s"""https://www.gravatar.com/avatar/${StringUtil.md5(mailAddress.toLowerCase)}?s=${size}"""
        } else {
          s"""${context.path}/_unknown/_avatar"""
        }
      }
    }

    if(tooltip){
      Html(s"""<img src="${src}" class="avatar" style="width: ${size}px; height: ${size}px;" data-toggle="tooltip" title="${userName}"/>""")
    } else {
      Html(s"""<img src="${src}" class="avatar" style="width: ${size}px; height: ${size}px;" />""")
    }
  }

}