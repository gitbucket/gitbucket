package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import util.JGitUtil

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
    Repositories insert
      Repository(
        userName         = userName,
        repositoryName   = repositoryName,
        isPrivate        = false,
        description      = description,
        defaultBranch    = "master",
        registeredDate   = currentDate,
        updatedDate      = currentDate,
        lastActivityDate = currentDate)
    
    IssueId insert (userName, repositoryName, 0)
  }

  def deleteRepository(userName: String, repositoryName: String): Unit = {
    Collaborators .filter(_.byRepository(userName, repositoryName)).delete
    IssueLabels   .filter(_.byRepository(userName, repositoryName)).delete
    Labels        .filter(_.byRepository(userName, repositoryName)).delete
    IssueComments .filter(_.byRepository(userName, repositoryName)).delete
    Issues        .filter(_.byRepository(userName, repositoryName)).delete
    IssueId       .filter(_.byRepository(userName, repositoryName)).delete
    Milestones    .filter(_.byRepository(userName, repositoryName)).delete
    Repositories  .filter(_.byRepository(userName, repositoryName)).delete
  }

  /**
   * Returns the repository names of the specified user.
   *
   * @param userName the user name of repository owner
   * @return the list of repository names
   */
  def getRepositoryNamesOfUser(userName: String): List[String] =
    Query(Repositories) filter(_.userName is userName.bind) map (_.repositoryName) list

  /**
   * Returns the list of specified user's repositories information.
   *
   * @param userName the user name
   * @param baseUrl the base url of this application
   * @param loginUserName the logged in user name
   * @return the list of repository information which is sorted in descending order of lastActivityDate.
   */
  def getVisibleRepositories(userName: String, baseUrl: String, loginUserName: Option[String]): List[RepositoryInfo] = {
    val q1 = Repositories
      .filter { t => t.userName is userName.bind }
      .map    { r => r }

    val q2 = Collaborators
      .innerJoin(Repositories).on((t1, t2) => t1.byRepository(t2.userName, t2.repositoryName))
      .filter{ case (t1, t2) => t1.collaboratorName is userName.bind}
      .map   { case (t1, t2) => t2 }

    def visibleFor(t: Repositories.type, loginUserName: Option[String]) = {
      loginUserName match {
        case Some(x) => (t.isPrivate is false.bind) || (
          (t.isPrivate is true.bind) && ((t.userName is x.bind) || (Collaborators.filter { c =>
            c.byRepository(t.userName, t.repositoryName) && (c.collaboratorName is x.bind)
          }.exists)))
        case None    => (t.isPrivate is false.bind)
      }
    }

    q1.union(q2).filter(visibleFor(_, loginUserName)).sortBy(_.lastActivityDate desc).list map { repository =>
      new RepositoryInfo(JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl), repository)
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
    (Query(Repositories) filter { t => t.byRepository(userName, repositoryName) } firstOption) map { repository =>
      new RepositoryInfo(JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl), repository)
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

    def newRepositoryInfo(repository: Repository): RepositoryInfo = {
      new RepositoryInfo(JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl), repository)
    }

    (account match {
      // for Administrators
      case Some(x) if(x.isAdmin) => Query(Repositories)
      // for Normal Users
      case Some(x) if(!x.isAdmin) =>
        Query(Repositories) filter { t => (t.isPrivate is false.bind) ||
          (Query(Collaborators).filter(t2 => t2.byRepository(t.userName, t.repositoryName) && (t2.collaboratorName is x.userName.bind)) exists)
        }
      // for Guests
      case None => Query(Repositories) filter(_.isPrivate is false.bind)
    }).sortBy(_.lastActivityDate desc).list.map(newRepositoryInfo _)
  }

  /**
   * Updates the last activity date of the repository.
   */
  def updateLastActivityDate(userName: String, repositoryName: String): Unit =
    Repositories.filter(_.byRepository(userName, repositoryName)).map(_.lastActivityDate).update(currentDate)
  
  /**
   * Save repository options.
   */
  def saveRepositoryOptions(userName: String, repositoryName: String, 
      description: Option[String], defaultBranch: String, isPrivate: Boolean): Unit =
    Repositories.filter(_.byRepository(userName, repositoryName))
      .map { r => r.description.? ~ r.defaultBranch ~ r.isPrivate ~ r.updatedDate }
      .update (description, defaultBranch, isPrivate, currentDate)

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
    Collaborators.filter(_.byPrimaryKey(userName, repositoryName, collaboratorName)).delete

  /**
   * Returns the list of collaborators name which is sorted with ascending order.
   * 
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @return the list of collaborators name
   */
  def getCollaborators(userName: String, repositoryName: String): List[String] =
    Query(Collaborators).filter(_.byRepository(userName, repositoryName)).sortBy(_.collaboratorName).map(_.collaboratorName).list

  def hasWritePermission(owner: String, repository: String, loginAccount: Option[Account]): Boolean = {
    loginAccount match {
      case Some(a) if(a.isAdmin) => true
      case Some(a) if(a.userName == owner) => true
      case Some(a) if(getCollaborators(owner, repository).contains(a.userName)) => true
      case _ => false
    }
  }

}

object RepositoryService {

  case class RepositoryInfo(owner: String, name: String, url: String, repository: Repository,
    commitCount: Int, branchList: List[String], tags: List[util.JGitUtil.TagInfo]){

    def this(repo: JGitUtil.RepositoryInfo, model: Repository) = {
      this(repo.owner, repo.name, repo.url, model, repo.commitCount, repo.branchList, repo.tags)
    }

  }

}