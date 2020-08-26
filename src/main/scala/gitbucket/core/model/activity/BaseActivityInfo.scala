package gitbucket.core.model.activity

import gitbucket.core.model.Activity

trait BaseActivityInfo {
  def toActivity: Activity
}
