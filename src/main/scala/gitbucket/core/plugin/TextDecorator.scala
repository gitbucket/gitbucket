package gitbucket.core.plugin

import gitbucket.core.controller.Context

trait TextDecorator {

  def decorate(text: String)(implicit context: Context): String

}
