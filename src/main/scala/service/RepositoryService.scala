package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import util.JGitUtil
import javax.servlet.ServletContext

trait RepositoryService { self: AccountService =>
  import RepositoryService._

  /**
   * Creates a new repository.
   *
   * The project is created as public repository at first. Users can modify the project type at the repository settings
   * page after the project creation to configure the project as the private repository.
   *
   * @param repositoryName the repository name
   * @param userName the user name of the repository owner
   * @param description the repository description
   */
  def createRepository(repositoryName: String, userName: String, description: Option[String]): Unit = {
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
   * Returns the list of specified user's repositories information.
   *
   * @param userName the user name
   * @param servletContext the servlet context
   * @return the list of repository information which is sorted in descending order of lastActivityDate.
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
   * @param userName the user name of the repository owner
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
   * Returns the list of accessible repositories information for the specified account user.
   * 
   * @param account the account
   * @param servletContext the servlet context
   * @return the repository informations which is sorted in descending order of lastActivityDate.
   */
  def getAccessibleRepositories(account: Option[Account], servletContext: ServletContext): List[RepositoryInfo] = {
    account match {
      // for Administrators
      case Some(x) if(x.userType == AccountService.Administrator) => {
        (Query(Repositories) sortBy(_.lastActivityDate desc) list) map { repository =>
          val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, servletContext)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
      // for Normal Users
      case Some(x) if(x.userType == AccountService.Normal) => {
        // TODO only repositories registered as collaborator
        (Query(Repositories) sortBy(_.lastActivityDate desc) list) map { repository =>
          val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, servletContext)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
      // for Guests
      case None => {
        (Query(Repositories) filter(_.repositoryType is Public.bind) sortBy(_.lastActivityDate desc) list) map { repository =>
          val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, servletContext)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
    }
  }

  /**
   * TODO Updates the last activity date of the repository.
   */
  def updateLastActivityDate(userName: String, repositoryName: String): Unit = {
    
  }

  /**
   * Add collaborator to the repository.
   *
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @param collaboratorName the collaborator name
   */
  def addCollaborator(userName: String, repositoryName: String, collaboratorName: String): Unit =
    Collaborators.* insert(Collaborator(userName, repositoryName, collaboratorName))

  def getCollaborators(userName: String, repositoryName: String): List[String] =
    (Query(Collaborators) filter { collaborator =>
      (collaborator.userName is userName.bind) && (collaborator.repositoryName is repositoryName.bind)
    } sortBy(_.collaboratorName) list) map(_.collaboratorName)

}

object RepositoryService {

  val Public = 0
  val Private = 1

  case class RepositoryInfo(owner: String, name: String, url: String, repository: Repository, branchList: List[String], tags: List[util.JGitUtil.TagInfo])
}