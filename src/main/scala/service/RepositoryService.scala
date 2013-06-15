package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import util.JGitUtil
import javax.servlet.ServletContext
import scala.Some
import model.Repository
import model.Account
import model.Collaborator

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

    Repositories insert
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

  def deleteRepository(userName: String, repositoryName: String): Unit = {
    Collaborators
      .filter { c => (c.userName is userName.bind) && (c.repositoryName is repositoryName.bind) }
      .delete

    Repositories
      .filter { r => (r.userName is userName.bind) && (r.repositoryName is repositoryName.bind) }
      .delete
  }

  /**
   * Returns the list of specified user's repositories information.
   *
   * @param userName the user name
   * @param baseUrl the base url of this application
   * @return the list of repository information which is sorted in descending order of lastActivityDate.
   */
  def getRepositoriesOfUser(userName: String, baseUrl: String): List[RepositoryInfo] = {
    val q1 = Repositories
      .filter { r => r.userName is userName.bind }
      .map    { r => r }

    val q2 = Collaborators
      .innerJoin(Repositories).on((c, r) => (c.userName is r.userName) && (c.repositoryName is r.repositoryName))
      .filter{ case (c, r) => c.collaboratorName is userName.bind}
      .map   { case (c, r) => r }

    q1.union(q2).sortBy(_.lastActivityDate).list map { repository =>
      val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
      RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
    }
  }

  /**
   * Returns the specified repository information.
   * 
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @param baseUrl the base url of this application
   * @return the repository information
   */
  def getRepository(userName: String, repositoryName: String, baseUrl: String): Option[RepositoryInfo] = {
    (Query(Repositories) filter { repository =>
      (repository.userName is userName.bind) && (repository.repositoryName is repositoryName.bind)
    } firstOption) map { repository =>
      val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
      RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
    }
  }

  /**
   * Returns the list of accessible repositories information for the specified account user.
   * 
   * @param account the account
   * @param baseUrl the base url of this application
   * @return the repository informations which is sorted in descending order of lastActivityDate.
   */
  def getAccessibleRepositories(account: Option[Account], baseUrl: String): List[RepositoryInfo] = {
    account match {
      // for Administrators
      case Some(x) if(x.userType == AccountService.Administrator) => {
        (Query(Repositories) sortBy(_.lastActivityDate desc) list) map { repository =>
          val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
      // for Normal Users
      case Some(x) if(x.userType == AccountService.Normal) => {
        // TODO only repositories registered as collaborator
        (Query(Repositories) sortBy(_.lastActivityDate desc) list) map { repository =>
          val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
      // for Guests
      case None => {
        (Query(Repositories) filter(_.repositoryType is Public.bind) sortBy(_.lastActivityDate desc) list) map { repository =>
          val repositoryInfo = JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, repository, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
    }
  }

  /**
   * Updates the last activity date of the repository.
   */
  def updateLastActivityDate(userName: String, repositoryName: String): Unit =
    Query(Repositories)
      .filter { r => (r.userName is userName.bind) && (r.repositoryName is repositoryName.bind) }
      .map    { _.lastActivityDate }
      .update (new java.sql.Date(System.currentTimeMillis))
  
  /**
   * Save repository options.
   */
  def saveRepositoryOptions(userName: String, repositoryName: String, 
      description: Option[String], defaultBranch: String, repositoryType: Int): Unit =
    Query(Repositories)
      .filter { r => (r.userName is userName.bind) && (r.repositoryName is repositoryName.bind) }
      .map    { r => r.description.? ~ r.defaultBranch ~ r.repositoryType ~ r.updatedDate }
      .update (description, defaultBranch, repositoryType, new java.sql.Date(System.currentTimeMillis))

  /**
   * Add collaborator to the repository.
   *
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @param collaboratorName the collaborator name
   */
  def addCollaborator(userName: String, repositoryName: String, collaboratorName: String): Unit =
    Collaborators insert(Collaborator(userName, repositoryName, collaboratorName))

  /**
   * Remove collaborator from the repository.
   * 
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @param collaboratorName the collaborator name
   */
  def removeCollaborator(userName: String, repositoryName: String, collaboratorName: String): Unit =
    (Query(Collaborators) filter { c =>
        (c.userName is userName.bind) && (c.repositoryName is repositoryName.bind) && (c.collaboratorName is collaboratorName.bind)
    }).delete
  
    
  /**
   * Returns the list of collaborators name which is sorted with ascending order.
   * 
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @return the list of collaborators name
   */
  def getCollaborators(userName: String, repositoryName: String): List[String] =
    (Query(Collaborators) filter { c =>
      (c.userName is userName.bind) && (c.repositoryName is repositoryName.bind)
    } sortBy(_.collaboratorName) list) map(_.collaboratorName)

}

object RepositoryService {

  val Public = 0
  val Private = 1

  case class RepositoryInfo(owner: String, name: String, url: String, repository: Repository, branchList: List[String], tags: List[util.JGitUtil.TagInfo])
}