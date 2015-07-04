package gitbucket.core.plugin

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import gitbucket.core.service.SystemSettingsService.SystemSettings

case class GitRepositoryRouting(urlPattern: String, localPath: String, filter: GitRepositoryFilter){
  def this(urlPattern: String, localPath: String) = {
    this(urlPattern, localPath, new GitRepositoryFilter(){
      def filter(request: HttpServletRequest, response: HttpServletResponse, settings: SystemSettings, isUpdating: Boolean): Boolean = true
    })
  }
}

trait GitRepositoryFilter {
  def filter(request: HttpServletRequest, response: HttpServletResponse, settings: SystemSettings, isUpdating: Boolean): Boolean
}