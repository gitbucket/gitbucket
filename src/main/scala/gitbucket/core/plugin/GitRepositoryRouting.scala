package gitbucket.core.plugin

import gitbucket.core.model.Session
import gitbucket.core.service.SystemSettingsService.SystemSettings

case class GitRepositoryRouting(urlPattern: String, localPath: String, filter: GitRepositoryFilter){

  def this(urlPattern: String, localPath: String) = {
    this(urlPattern, localPath, new GitRepositoryFilter(){
      def filter(repositoryName: String, userName: Option[String], settings: SystemSettings, isUpdating: Boolean)
                (implicit session: Session): Boolean = true
    })
  }

}

trait GitRepositoryFilter {
  def filter(path: String, userName: Option[String], settings: SystemSettings, isUpdating: Boolean)
            (implicit session: Session): Boolean
}