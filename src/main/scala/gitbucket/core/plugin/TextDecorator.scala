package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService.RepositoryInfo

trait TextDecorator {

  def decorate(text: String, repository: RepositoryInfo)(implicit context: Context): String

}
