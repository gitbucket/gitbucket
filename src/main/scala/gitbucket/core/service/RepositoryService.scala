package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.{Collaborator, Repository, RepositoryOptions, Account, Role}
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
  def insertRepository(repositoryName: String, userName: String, description: Option[String], isPrivate: Boolean,
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
        parentRepositoryName = parentRepositoryName,
        options              = RepositoryOptions(
          issuesOption         = "PUBLIC", // TODO DISABLE for the forked repository?
          externalIssuesUrl    = None,
          wikiOption           = "PUBLIC", // TODO DISABLE for the forked repository?
          externalWikiUrl      = None,
          allowFork            = true
        )
      )

    IssueId insert (userName, repositoryName, 0)
  }

  def renameRepository(oldUserName: String, oldRepositoryName: String, newUserName: String, newRepositoryName: String)
                      (implicit s: Session): Unit = {
    getAccountByUserName(newUserName).foreach { account =>
      (Repositories filter { t => t.byRepository(oldUserName, oldRepositoryName) } firstOption).map { repository =>
        Repositories insert repository.copy(userName = newUserName, repositoryName = newRepositoryName)

        val webHooks                = WebHooks               .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val webHookEvents           = WebHookEvents          .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val milestones              = Milestones             .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val issueId                 = IssueId                .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val issues                  = Issues                 .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val pullRequests            = PullRequests           .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val labels                  = Labels                 .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val issueComments           = IssueComments          .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val issueLabels             = IssueLabels            .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val commitComments          = CommitComments         .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val commitStatuses          = CommitStatuses         .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val collaborators           = Collaborators          .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val protectedBranches       = ProtectedBranches      .filter(_.byRepository(oldUserName, oldRepositoryName)).list
        val protectedBranchContexts = ProtectedBranchContexts.filter(_.byRepository(oldUserName, oldRepositoryName)).list

        Repositories.filter { t =>
          (t.originUserName === oldUserName.bind) && (t.originRepositoryName === oldRepositoryName.bind)
        }.map { t => t.originUserName -> t.originRepositoryName }.update(newUserName, newRepositoryName)

        Repositories.filter { t =>
          (t.parentUserName === oldUserName.bind) && (t.parentRepositoryName === oldRepositoryName.bind)
        }.map { t => t.originUserName -> t.originRepositoryName }.update(newUserName, newRepositoryName)

        // Updates activity fk before deleting repository because activity is sorted by activityId
        // and it can't be changed by deleting-and-inserting record.
        Activities.filter(_.byRepository(oldUserName, oldRepositoryName)).list.foreach { activity =>
          Activities.filter(_.activityId === activity.activityId.bind)
            .map(x => (x.userName, x.repositoryName)).update(newUserName, newRepositoryName)
        }

        deleteRepository(oldUserName, oldRepositoryName)

        WebHooks     .insertAll(webHooks      .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        WebHookEvents.insertAll(webHookEvents .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        Milestones   .insertAll(milestones    .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        IssueId      .insertAll(issueId       .map(_.copy(_1       = newUserName, _2             = newRepositoryName)) :_*)

        val newMilestones = Milestones.filter(_.byRepository(newUserName, newRepositoryName)).list
        Issues.insertAll(issues.map { x => x.copy(
          userName       = newUserName,
          repositoryName = newRepositoryName,
          milestoneId    = x.milestoneId.map { id =>
            newMilestones.find(_.title == milestones.find(_.milestoneId == id).get.title).get.milestoneId
          }
        )} :_*)

        PullRequests           .insertAll(pullRequests  .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        IssueComments          .insertAll(issueComments .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        Labels                 .insertAll(labels        .map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        CommitComments         .insertAll(commitComments.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        CommitStatuses         .insertAll(commitStatuses.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        ProtectedBranches      .insertAll(protectedBranches.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)
        ProtectedBranchContexts.insertAll(protectedBranchContexts.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)

        // Update source repository of pull requests
        PullRequests.filter { t =>
          (t.requestUserName === oldUserName.bind) && (t.requestRepositoryName === oldRepositoryName.bind)
        }.map { t => t.requestUserName -> t.requestRepositoryName }.update(newUserName, newRepositoryName)

        // Convert labelId
        val oldLabelMap = labels.map(x => (x.labelId, x.labelName)).toMap
        val newLabelMap = Labels.filter(_.byRepository(newUserName, newRepositoryName)).map(x => (x.labelName, x.labelId)).list.toMap
        IssueLabels.insertAll(issueLabels.map(x => x.copy(
          labelId        = newLabelMap(oldLabelMap(x.labelId)),
          userName       = newUserName,
          repositoryName = newRepositoryName
        )) :_*)

        // TODO Drop transfered owner from collaborators?
        Collaborators.insertAll(collaborators.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)) :_*)

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
    WebHookEvents .filter(_.byRepository(userName, repositoryName)).delete
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
   * @return the repository information
   */
  def getRepository(userName: String, repositoryName: String)(implicit s: Session): Option[RepositoryInfo] = {
    (Repositories filter { t => t.byRepository(userName, repositoryName) } firstOption) map { repository =>
      // for getting issue count and pull request count
      val issues = Issues.filter { t =>
        t.byRepository(repository.userName, repository.repositoryName) && (t.closed === false.bind)
      }.map(_.pullRequest).list

      new RepositoryInfo(
        JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName),
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
   * Returns the repositories except private repository that user does not have access right.
   * Include public repository, private own repository and private but collaborator repository.
   *
   * @param userName the user name of collaborator
   * @return the repository information list
   */
  def getAllRepositories(userName: String)(implicit s: Session): List[(String, String)] = {
    Repositories.filter { t1 =>
      (t1.isPrivate === false.bind) ||
      (t1.userName  === userName.bind) || (t1.userName in (GroupMembers.filter(_.userName === userName.bind).map(_.groupName))) ||
      (Collaborators.filter { t2 => t2.byRepository(t1.userName, t1.repositoryName) &&
        ((t2.collaboratorName === userName.bind) || (t2.collaboratorName in GroupMembers.filter(_.userName === userName.bind).map(_.groupName)))
      } exists)
    }.sortBy(_.lastActivityDate desc).map{ t =>
      (t.userName, t.repositoryName)
    }.list
  }

  def getUserRepositories(userName: String, withoutPhysicalInfo: Boolean = false)
                         (implicit s: Session): List[RepositoryInfo] = {
    Repositories.filter { t1 =>
      (t1.userName === userName.bind) || (t1.userName in (GroupMembers.filter(_.userName === userName.bind).map(_.groupName))) ||
      (Collaborators.filter { t2 => t2.byRepository(t1.userName, t1.repositoryName) &&
        ((t2.collaboratorName === userName.bind) || (t2.collaboratorName in GroupMembers.filter(_.userName === userName.bind).map(_.groupName)))
      } exists)
    }.sortBy(_.lastActivityDate desc).list.map{ repository =>
      new RepositoryInfo(
        if(withoutPhysicalInfo){
          new JGitUtil.RepositoryInfo(repository.userName, repository.repositoryName)
        } else {
          JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName)
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
   * @param repositoryUserName the repository owner (if None then returns all repositories which are visible for logged in user)
   * @param withoutPhysicalInfo if true then the result does not include physical repository information such as commit count,
   *                            branches and tags
   * @return the repository information which is sorted in descending order of lastActivityDate.
   */
  def getVisibleRepositories(loginAccount: Option[Account], repositoryUserName: Option[String] = None,
                             withoutPhysicalInfo: Boolean = false)
                            (implicit s: Session): List[RepositoryInfo] = {
    (loginAccount match {
      // for Administrators
      case Some(x) if(x.isAdmin) => Repositories
      // for Normal Users
      case Some(x) if(!x.isAdmin) =>
        Repositories filter { t =>
          (t.isPrivate === false.bind) || (t.userName === x.userName) ||
          (t.userName in GroupMembers.filter(_.userName === x.userName.bind).map(_.groupName)) ||
          (Collaborators.filter { t2 =>
            t2.byRepository(t.userName, t.repositoryName) &&
              ((t2.collaboratorName === x.userName.bind) || (t2.collaboratorName in GroupMembers.filter(_.userName === x.userName.bind).map(_.groupName)))
          } exists)
        }
      // for Guests
      case None => Repositories filter(_.isPrivate === false.bind)
    }).filter { t =>
      repositoryUserName.map { userName => t.userName === userName.bind } getOrElse LiteralColumn(true)
    }.sortBy(_.lastActivityDate desc).list.map{ repository =>
      new RepositoryInfo(
        if(withoutPhysicalInfo){
          new JGitUtil.RepositoryInfo(repository.userName, repository.repositoryName)
        } else {
          JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName)
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
      description: Option[String], isPrivate: Boolean,
      issuesOption: String, externalIssuesUrl: Option[String],
      wikiOption: String, externalWikiUrl: Option[String],
      allowFork: Boolean)(implicit s: Session): Unit =
    Repositories.filter(_.byRepository(userName, repositoryName))
      .map { r => (r.description.?, r.isPrivate, r.issuesOption, r.externalIssuesUrl.?, r.wikiOption, r.externalWikiUrl.?, r.allowFork, r.updatedDate) }
      .update (description, isPrivate, issuesOption, externalIssuesUrl, wikiOption, externalWikiUrl, allowFork, currentDate)

  def saveRepositoryDefaultBranch(userName: String, repositoryName: String,
      defaultBranch: String)(implicit s: Session): Unit =
    Repositories.filter(_.byRepository(userName, repositoryName))
      .map { r => r.defaultBranch }
      .update (defaultBranch)

  /**
   * Add collaborator (user or group) to the repository.
   */
  def addCollaborator(userName: String, repositoryName: String, collaboratorName: String, role: String)(implicit s: Session): Unit =
    Collaborators insert Collaborator(userName, repositoryName, collaboratorName, role)

  /**
   * Remove all collaborators from the repository.
   */
  def removeCollaborators(userName: String, repositoryName: String)(implicit s: Session): Unit =
    Collaborators.filter(_.byRepository(userName, repositoryName)).delete

  /**
   * Returns the list of collaborators name (user name or group name) which is sorted with ascending order.
   */
  def getCollaborators(userName: String, repositoryName: String)(implicit s: Session): List[(Collaborator, Boolean)] =
    Collaborators
      .innerJoin(Accounts).on(_.collaboratorName === _.userName)
      .filter { case (t1, t2) => t1.byRepository(userName, repositoryName) }
      .map { case (t1, t2) => (t1, t2.groupAccount) }
      .sortBy { case (t1, t2) => t1.collaboratorName }
      .list

  /**
   * Returns the list of all collaborator name and permission which is sorted with ascending order.
   * If a group is added as a collaborator, this method returns users who are belong to that group.
   */
  def getCollaboratorUserNames(userName: String, repositoryName: String, filter: Seq[Role] = Nil)(implicit s: Session): List[String] = {
    val q1 = Collaborators
      .innerJoin(Accounts).on { case (t1, t2) => (t1.collaboratorName === t2.userName) && (t2.groupAccount === false.bind) }
      .filter { case (t1, t2) => t1.byRepository(userName, repositoryName) }
      .map { case (t1, t2) => (t1.collaboratorName, t1.role) }

    val q2 = Collaborators
      .innerJoin(Accounts).on { case (t1, t2) => (t1.collaboratorName === t2.userName) && (t2.groupAccount === true.bind) }
      .innerJoin(GroupMembers).on { case ((t1, t2), t3) => t2.userName === t3.groupName }
      .filter { case ((t1, t2), t3) => t1.byRepository(userName, repositoryName) }
      .map { case ((t1, t2), t3) => (t3.userName, t1.role) }

    q1.union(q2).list.filter { x => filter.isEmpty || filter.exists(_.name == x._2) }.map(_._1)
  }


  def hasDeveloperRole(owner: String, repository: String, loginAccount: Option[Account])(implicit s: Session): Boolean = {
    loginAccount match {
      case Some(a) if(a.isAdmin) => true
      case Some(a) if(a.userName == owner) => true
      case Some(a) if(getGroupMembers(owner).exists(_.userName == a.userName)) => true
      case Some(a) if(getCollaboratorUserNames(owner, repository, Seq(Role.ADMIN, Role.DEVELOPER)).contains(a.userName)) => true
      case _ => false
    }
  }

  def hasGuestRole(owner: String, repository: String, loginAccount: Option[Account])(implicit s: Session): Boolean = {
    loginAccount match {
      case Some(a) if(a.isAdmin) => true
      case Some(a) if(a.userName == owner) => true
      case Some(a) if(getGroupMembers(owner).exists(_.userName == a.userName)) => true
      case Some(a) if(getCollaboratorUserNames(owner, repository, Seq(Role.ADMIN, Role.DEVELOPER, Role.GUEST)).contains(a.userName)) => true
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

  case class RepositoryInfo(owner: String, name: String, repository: Repository,
    issueCount: Int, pullCount: Int, commitCount: Int, forkedCount: Int,
    branchList: Seq[String], tags: Seq[JGitUtil.TagInfo], managers: Seq[String]) {

    /**
     * Creates instance with issue count and pull request count.
     */
    def this(repo: JGitUtil.RepositoryInfo, model: Repository, issueCount: Int, pullCount: Int, forkedCount: Int, managers: Seq[String]) =
      this(
        repo.owner, repo.name, model,
        issueCount, pullCount, repo.commitCount, forkedCount,
        repo.branchList, repo.tags, managers)

    /**
     * Creates instance without issue count and pull request count.
     */
    def this(repo: JGitUtil.RepositoryInfo, model: Repository, 	forkedCount: Int, managers: Seq[String]) =
      this(
        repo.owner, repo.name, model,
        0, 0, repo.commitCount, forkedCount,
        repo.branchList, repo.tags, managers)

    def httpUrl(implicit context: Context): String = RepositoryService.httpUrl(owner, name)
    def sshUrl(implicit context: Context): Option[String] = RepositoryService.sshUrl(owner, name)

    def splitPath(path: String): (String, String) = {
      val id = branchList.collectFirst {
        case branch if(path == branch || path.startsWith(branch + "/")) => branch
      } orElse tags.collectFirst {
        case tag if(path == tag.name || path.startsWith(tag.name + "/")) => tag.name
      } getOrElse path.split("/")(0)

      (id, path.substring(id.length).stripPrefix("/"))
    }

  }

  def httpUrl(owner: String, name: String)(implicit context: Context): String = s"${context.baseUrl}/git/${owner}/${name}.git"
  def sshUrl(owner: String, name: String)(implicit context: Context): Option[String] =
    if(context.settings.ssh){
      context.settings.sshAddress.map { x => s"ssh://${x.genericUser}@${x.host}:${x.port}/${owner}/${name}.git" }
    } else None
  def openRepoUrl(openUrl: String)(implicit context: Context): String = s"github-${context.platform}://openRepo/${openUrl}"

}
