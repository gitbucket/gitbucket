package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import util.JGitUtil
import javax.servlet.ServletContext

trait ProjectService { self: AccountService =>
  import ProjectService._

  /**
   * Creates a new project.
   *
   * The project is created as public repository at first. Users can modify the project type at the repository settings
   * page after the project creation to configure the project as the private repository.
   *
   * @param repositoryName the repository name
   * @param userName the user name of the project owner
   * @param description the project description
   * @return the created project id
   */
  def createProject(repositoryName: String, userName: String, description: Option[String]): Long = {
    // TODO create a git repository also here?
    
    // TODO insert default labels.

    val currentDate = new java.sql.Date(System.currentTimeMillis)

    Repositories.* insert
      Repository(
        repositoryName   = repositoryName,
        userName         = userName,
        repositoryType   = Public,
        description      = description,
        defaultBranch    = "master",
        registeredDate   = currentDate,
        updatedDate      = currentDate,
        lastActivityDate = currentDate)
  }

  /**
   * Returns the specified user's repository informations.
   *
   * @param userName the user name
   * @param servletContext the servlet context
   * @return the repository informations which is sorted in descending order of lastActivityDate.
   */
  def getRepositoriesOfUser(userName: String, servletContext: ServletContext): List[RepositoryInfo] = {
    (Query(Repositories) filter(_.userName is userName.bind) sortBy(_.lastActivityDate desc) list) map { repository =>
      val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, servletContext)
      RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
    }
  }

  /**
   * Returns the specified repository information.
   * 
   * @param userName the user name
   * @param repositoryName the repository name
   * @param servletContext the servlet context
   * @return the repository information
   */
  def getRepository(userName: String, repositoryName: String, servletContext: ServletContext): Option[RepositoryInfo] = {
    (Query(Repositories) filter { repository =>
      (repository.userName is userName.bind) && (repository.repositoryName is repositoryName.bind)
    } firstOption) map { repository =>
      val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, servletContext)
      RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
    }
  }

  /**
   * Returns the accessible repository informations for the specified account user.
   * 
   * @param account the account
   * @param servletContext the servlet context
   * @return the repository informations which is sorted in descending order of lastActivityDate.
   */
  def getAccessibleRepositories(account: Option[Account], servletContext: ServletContext): List[RepositoryInfo] = {
    account match {
      case Some(x) => {
        (Query(Repositories) sortBy(_.lastActivityDate desc) list) map { repository =>
          val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, servletContext)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
      case None => {
        (Query(Repositories) filter(_.repositoryType is Public.bind) sortBy(_.lastActivityDate desc) list) map { repository =>
          val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, servletContext)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
    }
  }
  
  /**
   * Updates the last activity date of the project.
   */
  def updateLastActivityDate(userName: String, projectName: String): Unit = {
    
  }

}

object ProjectService {

  val Public = 0
  val Private = 1

  case class RepositoryInfo(owner: String, name: String, url: String, repository: Repository, branchList: List[String], tags: List[util.JGitUtil.TagInfo])
}