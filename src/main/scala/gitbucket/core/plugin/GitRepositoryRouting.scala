package gitbucket.core.plugin

import gitbucket.core.model.Session
import gitbucket.core.service.SystemSettingsService.SystemSettings

/**
 * Define the Git repository routing.
 *
 * @param urlPattern the regular expression which matches the repository path (e.g. "gist/(.+?)/(.+?)\\.git")
 * @param localPath the string to assemble local file path of repository (e.g. "gist/$1/$2")
 * @param filter the filter for request to the Git repository which is defined by this routing
 */
case class GitRepositoryRouting(urlPattern: String, localPath: String, filter: GitRepositoryFilter){

  def this(urlPattern: String, localPath: String) = {
    this(urlPattern, localPath, new GitRepositoryFilter(){
      def filter(repositoryName: String, userName: Option[String], settings: SystemSettings, isUpdating: Boolean)
                (implicit session: Session): Boolean = true
    })
  }

}

/**
 * Filters request to plug-in served repository. This is used to provide authentication mainly.
 */
trait GitRepositoryFilter {

  /**
   * Filters request to Git repository. If this method returns true then request is accepted.
   *
   * @param path the repository path which starts with '/'
   * @param userName the authenticated user name or None
   * @param settings the system settings
   * @param isUpdating true if update request, otherwise false
   * @param session the database session
   * @return true if allow accessing to repository, otherwise false.
   */
  def filter(path: String, userName: Option[String], settings: SystemSettings, isUpdating: Boolean)
            (implicit session: Session): Boolean

}