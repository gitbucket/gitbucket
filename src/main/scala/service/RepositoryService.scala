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
   * @param repositoryName the repository name
   * @param userName the user name of the repository owner
   * @param description the repository description
   * @param isPrivate the repository type (private is true, otherwise false)
   * @param originRepositoryName specify for the forked repository. (default is None)
   * @param originUserName specify for the forked repository. (default is None)
   */
  def createRepository(repositoryName: String, userName: String, description: Option[String], isPrivate: Boolean,
                       originRepositoryName: Option[String] = None, originUserName: Option[String] = None,
                       parentRepositoryName: Option[String] = None, parentUserName: Option[String] = None): Unit = {
    Repositories insert
      Repository(
        userName             = userName,
        repositoryName       = repositoryName,
        isPrivate            = isPrivate,
        description          = description,
        defaultBranch        = "master",
        registeredDate       = currentDate,
        updatedDate          = currentDate,
        lastActivityDate     = currentDate,
        originUserName       = originUserName,
        originRepositoryName = originRepositoryName,
        parentUserName       = parentUserName,
        parentRepositoryName = parentRepositoryName)

    IssueId insert (userName, repositoryName, 0)
  }

  def deleteRepository(userName: String, repositoryName: String): Unit = {
    Activities    .filter(_.byRepository(userName, repositoryName)).delete
    CommitLog     .filter(_.byRepository(userName, repositoryName)).delete
    Collaborators .filter(_.byRepository(userName, repositoryName)).delete
    IssueLabels   .filter(_.byRepository(userName, repositoryName)).delete
    Labels        .filter(_.byRepository(userName, repositoryName)).delete
    IssueComments .filter(_.byRepository(userName, repositoryName)).delete
    Issues        .filter(_.byRepository(userName, repositoryName)).delete
    PullRequests  .filter(_.byRepository(userName, repositoryName)).delete
    IssueId       .filter(_.byRepository(userName, repositoryName)).delete
    Milestones    .filter(_.byRepository(userName, repositoryName)).delete
    WebHooks      .filter(_.byRepository(userName, repositoryName)).delete
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
   * Returns the specified repository information.
   * 
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @param baseUrl the base url of this application
   * @return the repository information
   */
  def getRepository(userName: String, repositoryName: String, baseUrl: String): Option[RepositoryInfo] = {
    (Query(Repositories) filter { t => t.byRepository(userName, repositoryName) } firstOption) map { repository =>
      // for getting issue count and pull request count
      val issues = Query(Issues).filter { t =>
        t.byRepository(repository.userName, repository.repositoryName) && (t.closed is false.bind)
      }.map(_.pullRequest).list

      new RepositoryInfo(
        JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl),
        repository,
        issues.size,
        issues.filter(_ == true).size,
        getForkedCount(
          repository.originUserName.getOrElse(repository.userName),
          repository.originRepositoryName.getOrElse(repository.repositoryName)
        ))
    }
  }

  def getUserRepositories(userName: String, baseUrl: String): List[RepositoryInfo] = {
    Query(Repositories).filter { t1 =>
      (t1.userName is userName.bind) ||
        (Query(Collaborators).filter { t2 => t2.byRepository(t1.userName, t1.repositoryName) && (t2.collaboratorName is userName.bind)} exists)
    }.sortBy(_.lastActivityDate desc).list.map{ repository =>
      new RepositoryInfo(
        JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl),
        repository,
        getForkedCount(
          repository.originUserName.getOrElse(repository.userName),
          repository.originRepositoryName.getOrElse(repository.repositoryName)
        ))
    }
  }

  /**
   * Returns the list of visible repositories for the specified user.
   * If repositoryUserName is given then filters results by repository owner.
   *
   * @param loginAccount the logged in account
   * @param baseUrl the base url of this application
   * @param repositoryUserName the repository owner (if None then returns all repositories which are visible for logged in user)
   * @return the repository information which is sorted in descending order of lastActivityDate.
   */
  def getVisibleRepositories(loginAccount: Option[Account], baseUrl: String, repositoryUserName: Option[String] = None): List[RepositoryInfo] = {
    (loginAccount match {
      // for Administrators
      case Some(x) if(x.isAdmin) => Query(Repositories)
      // for Normal Users
      case Some(x) if(!x.isAdmin) =>
        Query(Repositories) filter { t => (t.isPrivate is false.bind) ||
          (Query(Collaborators).filter { t2 => t2.byRepository(t.userName, t.repositoryName) && (t2.collaboratorName is x.userName.bind)} exists)
        }
      // for Guests
      case None => Query(Repositories) filter(_.isPrivate is false.bind)
    }).filter { t =>
      repositoryUserName.map { userName => t.userName is userName.bind } getOrElse ConstColumn.TRUE
    }.sortBy(_.lastActivityDate desc).list.map{ repository =>
      new RepositoryInfo(
        JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl),
        repository,
        getForkedCount(
          repository.originUserName.getOrElse(repository.userName),
          repository.originRepositoryName.getOrElse(repository.repositoryName)
        ))
    }
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
   * Remove all collaborators from the repository.
   *
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   */
  def removeCollaborators(userName: String, repositoryName: String): Unit =
    Collaborators.filter(_.byRepository(userName, repositoryName)).delete

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

  private def getForkedCount(userName: String, repositoryName: String): Int =
    Query(Repositories.filter { t =>
      (t.originUserName is userName.bind) && (t.originRepositoryName is repositoryName.bind)
    }.length).first


  def getForkedRepositories(userName: String, repositoryName: String): List[String] =
    Query(Repositories).filter { t =>
      (t.originUserName is userName.bind) && (t.originRepositoryName is repositoryName.bind)
    }
    .sortBy(_.userName asc).map(_.userName).list

}

object RepositoryService {

  case class RepositoryInfo(owner: String, name: String, url: String, repository: Repository,
    issueCount: Int, pullCount: Int, commitCount: Int, forkedCount: Int,
    branchList: List[String], tags: List[util.JGitUtil.TagInfo]){

    /**
     * Creates instance with issue count and pull request count.
     */
    def this(repo: JGitUtil.RepositoryInfo, model: Repository, issueCount: Int, pullCount: Int, forkedCount: Int) =
      this(repo.owner, repo.name, repo.url, model, issueCount, pullCount, repo.commitCount, forkedCount, repo.branchList, repo.tags)

    /**
     * Creates instance without issue count and pull request count.
     */
    def this(repo: JGitUtil.RepositoryInfo, model: Repository, forkedCount: Int) =
      this(repo.owner, repo.name, repo.url, model, 0, 0, repo.commitCount, forkedCount, repo.branchList, repo.tags)
  }

  case class RepositoryTreeNode(owner: String, name: String, children: List[RepositoryTreeNode])

}