package gitbucket.core.model.activity

import gitbucket.core.model.Activity

trait BaseActivityInfo {

  def toActivity: Activity

  protected def trimInfoString(str: String, maxLen: Int): String =
    if (str.length > maxLen) s"${str.substring(0, maxLen)}..."
    else str
}
