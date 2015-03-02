package gitbucket.core.service

import gitbucket.core.model.{Collaborator, Repository, Account}
import gitbucket.core.model.Profile._
import gitbucket.core.util.JGitUtil
import profile.simple._

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
                       parentRepositoryName: Option[String] = None, parentUserName: Option[String] = None)
                      (implicit s: Session): Unit = {
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

  def renameRepository(oldUserName: String, oldRepositoryName: String, newUserName: String, newRepositoryName: String)
                      (implicit s: Session): Unit = {
    getAccountByUserName(newUserName).foreach { account =>
      (Repositories filter { t => t.byRepository(oldUserName, oldRepositoryName) } firstOption).map { repository =>
        Repositories insert repository.copy(userName = newUserName, repositoryName = newRepositoryName)

        val webHooks       = WebHooks      .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val milestones     = Milestones    .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val issueId        = IssueId       .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val issues         = Issues        .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val pullRequests   = PullRequests  .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val labels         = Labels        .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val issueComments  = IssueComments .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val issueLabels    = IssueLabels   .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val commitComments = CommitComments.filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val collaborators  = Collaborators .filter(_.byRepository(oldUserName, oldRepositoryName)).list

        Repositories.filter { t =>
          (t.originUserName === oldUserName.bind) && (t.originRepositoryName === oldRepositoryName.bind)
        }.map { t => t.originUserName -> t.originRepositoryName }.update(newUserName, newRepositoryName)

        Repositories.filter { t =>
          (t.parentUserName === oldUserName.bind) && (t.parentRepositoryName === oldRepositoryName.bind)
        }.map { t => t.originUserName -> t.originRepositoryName }.update(newUserName, newRepositoryName)

        PullRequests.filter { t =>
          t.requestRepositoryName === oldRepositoryName.bind
        }.map { t => t.requestUserName -> t.requestRepositoryName }.update(newUserName, newRepositoryName)

        // Updates activity fk before deleting repository because activity is sorted by activityId
        // and it can't be changed by deleting-and-inserting record.
        Activities.filter(_.byRepository(oldUserName, oldRepositoryName)).list.foreach { activity =>
          Activities.filter(_.activityId === activity.activityId.bind)
            .map(x => (x.userName, x.repositoryName)).update(newUserName, newRepositoryName)
        }

        deleteRepository(oldUserName, oldRepositoryName)

        WebHooks  .insertAll(webHooks      .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        Milestones.insertAll(milestones    .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        IssueId   .insertAll(issueId       .map(_.copy(_1       = newUserName, _2             = newRepositoryName)) :_*)

        val newMilestones = Milestones.filter(_.byRepository(newUserName, newRepositoryName)).list
        Issues.insertAll(issues.map { x => x.copy(
          userName       = newUserName,
          repositoryName = newRepositoryName,
          milestoneId    = x.milestoneId.map { id =>
            newMilestones.find(_.title == milestones.find(_.milestoneId == id).get.title).get.milestoneId
          }
        )} :_*)

        PullRequests  .insertAll(pullRequests  .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        IssueComments .insertAll(issueComments .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        Labels        .insertAll(labels        .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        CommitComments.insertAll(commitComments.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)

        // Convert labelId
        val oldLabelMap = labels.map(x => (x.labelId, x.labelName)).toMap
        val newLabelMap = Labels.filter(_.byRepository(newUserName, newRepositoryName)).map(x => (x.labelName, x.labelId)).list.toMap
        IssueLabels.insertAll(issueLabels.map(x => x.copy(
          labelId        = newLabelMap(oldLabelMap(x.labelId)),
          userName       = newUserName,
          repositoryName = newRepositoryName
        )) :_*)

        if(account.isGroupAccount){
          Collaborators.insertAll(getGroupMembers(newUserName).map(m => Collaborator(newUserName, newRepositoryName, m.userName)) :_*)
        } else {
          Collaborators.insertAll(collaborators.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        }

        // Update activity messages
        Activities.filter { t =>
          (t.message like s"%:${oldUserName}/${oldRepositoryName}]%") ||
          (t.message like s"%:${oldUserName}/${oldRepositoryName}#%") ||
          (t.message like s"%:${oldUserName}/${oldRepositoryName}@%")
        }.map { t => t.activityId -> t.message }.list.foreach { case (activityId, message) =>
          Activities.filter(_.activityId === activityId.bind).map(_.message).update(
            message
              .replace(s"[repo:${oldUserName}/${oldRepositoryName}]"   ,s"[repo:${newUserName}/${newRepositoryName}]")
              .replace(s"[branch:${oldUserName}/${oldRepositoryName}#" ,s"[branch:${newUserName}/${newRepositoryName}#")
              .replace(s"[tag:${oldUserName}/${oldRepositoryName}#"    ,s"[tag:${newUserName}/${newRepositoryName}#")
              .replace(s"[pullreq:${oldUserName}/${oldRepositoryName}#",s"[pullreq:${newUserName}/${newRepositoryName}#")
              .replace(s"[issue:${oldUserName}/${oldRepositoryName}#"  ,s"[issue:${newUserName}/${newRepositoryName}#")
              .replace(s"[commit:${oldUserName}/${oldRepositoryName}@" ,s"[commit:${newUserName}/${newRepositoryName}@")
          )
        }
      }
    }
  }

  def deleteRepository(userName: String, repositoryName: String)(implicit s: Session): Unit = {
    Activities    .filter(_.byRepository(userName, repositoryName)).delete
    Collaborators .filter(_.byRepository(userName, repositoryName)).delete
    CommitComments.filter(_.byRepository(userName, repositoryName)).delete
    IssueLabels   .filter(_.byRepository(userName, repositoryName)).delete
    Labels        .filter(_.byRepository(userName, repositoryName)).delete
    IssueComments .filter(_.byRepository(userName, repositoryName)).delete
    PullRequests  .filter(_.byRepository(userName, repositoryName)).delete
    Issues        .filter(_.byRepository(userName, repositoryName)).delete
    IssueId       .filter(_.byRepository(userName, repositoryName)).delete
    Milestones    .filter(_.byRepository(userName, repositoryName)).delete
    WebHooks      .filter(_.byRepository(userName, repositoryName)).delete
    Repositories  .filter(_.byRepository(userName, repositoryName)).delete

    // Update ORIGIN_USER_NAME and ORIGIN_REPOSITORY_NAME
    Repositories
      .filter { x => (x.originUserName === userName.bind) && (x.originRepositoryName === repositoryName.bind) }
      .map    { x => (x.userName, x.repositoryName) }
      .list
      .foreach { case (userName, repositoryName) =>
        Repositories
          .filter(_.byRepository(userName, repositoryName))
          .map(x => (x.originUserName?, x.originRepositoryName?))
          .update(None, None)
      }

    // Update PARENT_USER_NAME and PARENT_REPOSITORY_NAME
    Repositories
      .filter { x => (x.parentUserName === userName.bind) && (x.parentRepositoryName === repositoryName.bind) }
      .map    { x => (x.userName, x.repositoryName) }
      .list
      .foreach { case (userName, repositoryName) =>
        Repositories
          .filter(_.byRepository(userName, repositoryName))
          .map(x => (x.parentUserName?, x.parentRepositoryName?))
          .update(None, None)
      }
  }

  /**
   * Returns the repository names of the specified user.
   *
   * @param userName the user name of repository owner
   * @return the list of repository names
   */
  def getRepositoryNamesOfUser(userName: String)(implicit s: Session): List[String] =
    Repositories filter(_.userName === userName.bind) map (_.repositoryName) list

  /**
   * Returns the specified repository information.
   * 
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @param baseUrl the base url of this application
   * @return the repository information
   */
  def getRepository(userName: String, repositoryName: String, baseUrl: String)(implicit s: Session): Option[RepositoryInfo] = {
    (Repositories filter { t => t.byRepository(userName, repositoryName) } firstOption) map { repository =>
      // for getting issue count and pull request count
      val issues = Issues.filter { t =>
        t.byRepository(repository.userName, repository.repositoryName) && (t.closed === false.bind)
      }.map(_.pullRequest).list

      new RepositoryInfo(
        JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl),
        repository,
        issues.count(_ == false),
        issues.count(_ == true),
        getForkedCount(
          repository.originUserName.getOrElse(repository.userName),
          repository.originRepositoryName.getOrElse(repository.repositoryName)
        ),
        getRepositoryManagers(repository.userName))
    }
  }

  /**
   * Returns the repositories without private repository that user does not have access right.
   * Include public repository, private own repository and private but collaborator repository.
   *
   * @param userName the user name of collaborator
   * @return the repository infomation list
   */
  def getAllRepositories(userName: String)(implicit s: Session): List[(String, String)] = {
    Repositories.filter { t1 =>
      (t1.isPrivate === false.bind) ||
      (t1.userName  === userName.bind) ||
      (Collaborators.filter { t2 => t2.byRepository(t1.userName, t1.repositoryName) && (t2.collaboratorName === userName.bind)} exists)
    }.sortBy(_.lastActivityDate desc).map{ t =>
      (t.userName, t.repositoryName)
    }.list
  }

  def getUserRepositories(userName: String, baseUrl: String, withoutPhysicalInfo: Boolean = false)
                         (implicit s: Session): List[RepositoryInfo] = {
    Repositories.filter { t1 =>
      (t1.userName === userName.bind) ||
        (Collaborators.filter { t2 => t2.byRepository(t1.userName, t1.repositoryName) && (t2.collaboratorName === userName.bind)} exists)
    }.sortBy(_.lastActivityDate desc).list.map{ repository =>
      new RepositoryInfo(
        if(withoutPhysicalInfo){
          new JGitUtil.RepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
        } else {
          JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
        },
        repository,
        getForkedCount(
          repository.originUserName.getOrElse(repository.userName),
          repository.originRepositoryName.getOrElse(repository.repositoryName)
        ),
        getRepositoryManagers(repository.userName))
    }
  }

  /**
   * Returns the list of visible repositories for the specified user.
   * If repositoryUserName is given then filters results by repository owner.
   *
   * @param loginAccount the logged in account
   * @param baseUrl the base url of this application
   * @param repositoryUserName the repository owner (if None then returns all repositories which are visible for logged in user)
   * @param withoutPhysicalInfo if true then the result does not include physical repository information such as commit count,
   *                            branches and tags
   * @return the repository information which is sorted in descending order of lastActivityDate.
   */
  def getVisibleRepositories(loginAccount: Option[Account], baseUrl: String, repositoryUserName: Option[String] = None,
                             withoutPhysicalInfo: Boolean = false)
                            (implicit s: Session): List[RepositoryInfo] = {
    (loginAccount match {
      // for Administrators
      case Some(x) if(x.isAdmin) => Repositories
      // for Normal Users
      case Some(x) if(!x.isAdmin) =>
        Repositories filter { t => (t.isPrivate === false.bind) || (t.userName === x.userName) ||
          (Collaborators.filter { t2 => t2.byRepository(t.userName, t.repositoryName) && (t2.collaboratorName === x.userName.bind)} exists)
        }
      // for Guests
      case None => Repositories filter(_.isPrivate === false.bind)
    }).filter { t =>
      repositoryUserName.map { userName => t.userName === userName.bind } getOrElse LiteralColumn(true)
    }.sortBy(_.lastActivityDate desc).list.map{ repository =>
      new RepositoryInfo(
        if(withoutPhysicalInfo){
          new JGitUtil.RepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
        } else {
          JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName, baseUrl)
        },
        repository,
        getForkedCount(
          repository.originUserName.getOrElse(repository.userName),
          repository.originRepositoryName.getOrElse(repository.repositoryName)
        ),
        getRepositoryManagers(repository.userName))
    }
  }

  private def getRepositoryManagers(userName: String)(implicit s: Session): Seq[String] =
    if(getAccountByUserName(userName).exists(_.isGroupAccount)){
      getGroupMembers(userName).collect { case x if(x.isManager) => x.userName }
    } else {
      Seq(userName)
    }

  /**
   * Updates the last activity date of the repository.
   */
  def updateLastActivityDate(userName: String, repositoryName: String)(implicit s: Session): Unit =
    Repositories.filter(_.byRepository(userName, repositoryName)).map(_.lastActivityDate).update(currentDate)

  /**
   * Save repository options.
   */
  def saveRepositoryOptions(userName: String, repositoryName: String, 
      description: Option[String], defaultBranch: String, isPrivate: Boolean)(implicit s: Session): Unit =
    Repositories.filter(_.byRepository(userName, repositoryName))
      .map { r => (r.description.?, r.defaultBranch, r.isPrivate, r.updatedDate) }
      .update (description, defaultBranch, isPrivate, currentDate)

  /**
   * Add collaborator to the repository.
   *
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @param collaboratorName the collaborator name
   */
  def addCollaborator(userName: String, repositoryName: String, collaboratorName: String)(implicit s: Session): Unit =
    Collaborators insert Collaborator(userName, repositoryName, collaboratorName)

  /**
   * Remove collaborator from the repository.
   * 
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @param collaboratorName the collaborator name
   */
  def removeCollaborator(userName: String, repositoryName: String, collaboratorName: String)(implicit s: Session): Unit =
    Collaborators.filter(_.byPrimaryKey(userName, repositoryName, collaboratorName)).delete

  /**
   * Remove all collaborators from the repository.
   *
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   */
  def removeCollaborators(userName: String, repositoryName: String)(implicit s: Session): Unit =
    Collaborators.filter(_.byRepository(userName, repositoryName)).delete

  /**
   * Returns the list of collaborators name which is sorted with ascending order.
   * 
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @return the list of collaborators name
   */
  def getCollaborators(userName: String, repositoryName: String)(implicit s: Session): List[String] =
    Collaborators.filter(_.byRepository(userName, repositoryName)).sortBy(_.collaboratorName).map(_.collaboratorName).list

  def hasWritePermission(owner: String, repository: String, loginAccount: Option[Account])(implicit s: Session): Boolean = {
    loginAccount match {
      case Some(a) if(a.isAdmin) => true
      case Some(a) if(a.userName == owner) => true
      case Some(a) if(getCollaborators(owner, repository).contains(a.userName)) => true
      case _ => false
    }
  }

  private def getForkedCount(userName: String, repositoryName: String)(implicit s: Session): Int =
    Query(Repositories.filter { t =>
      (t.originUserName === userName.bind) && (t.originRepositoryName === repositoryName.bind)
    }.length).first


  def getForkedRepositories(userName: String, repositoryName: String)(implicit s: Session): List[(String, String)] =
    Repositories.filter { t =>
      (t.originUserName === userName.bind) && (t.originRepositoryName === repositoryName.bind)
    }
    .sortBy(_.userName asc).map(t => t.userName -> t.repositoryName).list

}

object RepositoryService {

  case class RepositoryInfo(owner: String, name: String, httpUrl: String, repository: Repository,
    issueCount: Int, pullCount: Int, commitCount: Int, forkedCount: Int,
    branchList: Seq[String], tags: Seq[JGitUtil.TagInfo], managers: Seq[String]){

    lazy val host = """^https?://(.+?)(:\d+)?/""".r.findFirstMatchIn(httpUrl).get.group(1)

    def sshUrl(port: Int, userName: String) = s"ssh://${userName}@${host}:${port}/${owner}/${name}.git"

    /**
     * Creates instance with issue count and pull request count.
     */
    def this(repo: JGitUtil.RepositoryInfo, model: Repository, issueCount: Int, pullCount: Int, forkedCount: Int, managers: Seq[String]) =
      this(repo.owner, repo.name, repo.url, model, issueCount, pullCount, repo.commitCount, forkedCount, repo.branchList, repo.tags, managers)

    /**
     * Creates instance without issue count and pull request count.
     */
    def this(repo: JGitUtil.RepositoryInfo, model: Repository, forkedCount: Int, managers: Seq[String]) =
      this(repo.owner, repo.name, repo.url, model, 0, 0, repo.commitCount, forkedCount, repo.branchList, repo.tags, managers)
  }

  case class RepositoryTreeNode(owner: String, name: String, children: List[RepositoryTreeNode])

}
