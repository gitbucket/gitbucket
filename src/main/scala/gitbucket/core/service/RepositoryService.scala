package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.util._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.model.{CommitComments => _, Session => _, _}
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile.dateColumnType
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.util.Directory.{getRepositoryDir, getRepositoryFilesDir, getTemporaryDir, getWikiRepositoryDir}
import gitbucket.core.util.JGitUtil.FileInfo
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.{Repository => _, _}
import scala.util.Using

trait RepositoryService {
  self: AccountService =>
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
  def insertRepository(
    repositoryName: String,
    userName: String,
    description: Option[String],
    isPrivate: Boolean,
    defaultBranch: String = "master",
    originRepositoryName: Option[String] = None,
    originUserName: Option[String] = None,
    parentRepositoryName: Option[String] = None,
    parentUserName: Option[String] = None
  )(implicit s: Session): Unit = {
    Repositories insert
      Repository(
        userName = userName,
        repositoryName = repositoryName,
        isPrivate = isPrivate,
        description = description,
        defaultBranch = defaultBranch,
        registeredDate = currentDate,
        updatedDate = currentDate,
        lastActivityDate = currentDate,
        originUserName = originUserName,
        originRepositoryName = originRepositoryName,
        parentUserName = parentUserName,
        parentRepositoryName = parentRepositoryName,
        options = RepositoryOptions(
          issuesOption = "PUBLIC", // TODO DISABLE for the forked repository?
          externalIssuesUrl = None,
          wikiOption = "PUBLIC", // TODO DISABLE for the forked repository?
          externalWikiUrl = None,
          allowFork = true,
          mergeOptions = "merge-commit,squash,rebase",
          defaultMergeOption = "merge-commit"
        )
      )

    IssueId insert (userName, repositoryName, 0)
  }

  def renameRepository(oldUserName: String, oldRepositoryName: String, newUserName: String, newRepositoryName: String)(
    implicit s: Session
  ): Unit = {
    getAccountByUserName(newUserName).foreach { account =>
      (Repositories filter { t =>
        t.byRepository(oldUserName, oldRepositoryName)
      } firstOption).foreach { repository =>
        LockUtil.lock(s"${repository.userName}/${repository.repositoryName}") {
          Repositories insert repository.copy(userName = newUserName, repositoryName = newRepositoryName)

          val webHooks = RepositoryWebHooks.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val webHookEvents = RepositoryWebHookEvents.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val milestones = Milestones.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val issueId = IssueId.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val issues = Issues.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val pullRequests = PullRequests.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val labels = Labels.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val priorities = Priorities.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val issueComments = IssueComments.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val issueLabels = IssueLabels.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val commitComments = CommitComments.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val commitStatuses = CommitStatuses.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val collaborators = Collaborators.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val protectedBranches = ProtectedBranches.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val protectedBranchContexts =
            ProtectedBranchContexts.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val deployKeys = DeployKeys.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val releases = ReleaseTags.filter(_.byRepository(oldUserName, oldRepositoryName)).list
          val releaseAssets = ReleaseAssets.filter(_.byRepository(oldUserName, oldRepositoryName)).list

          Repositories
            .filter { t =>
              (t.originUserName === oldUserName.bind) && (t.originRepositoryName === oldRepositoryName.bind)
            }
            .map { t =>
              t.originUserName -> t.originRepositoryName
            }
            .update(newUserName, newRepositoryName)

          Repositories
            .filter { t =>
              (t.parentUserName === oldUserName.bind) && (t.parentRepositoryName === oldRepositoryName.bind)
            }
            .map { t =>
              t.parentUserName -> t.parentRepositoryName
            }
            .update(newUserName, newRepositoryName)

          deleteRepositoryOnModel(oldUserName, oldRepositoryName)

          RepositoryWebHooks.insertAll(
            webHooks.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )
          RepositoryWebHookEvents.insertAll(
            webHookEvents.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )
          Milestones.insertAll(milestones.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*)
          Priorities.insertAll(priorities.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*)
          IssueId.insertAll(issueId.map(_.copy(_1 = newUserName, _2 = newRepositoryName)): _*)

          val newMilestones = Milestones.filter(_.byRepository(newUserName, newRepositoryName)).list
          val newPriorities = Priorities.filter(_.byRepository(newUserName, newRepositoryName)).list
          Issues.insertAll(issues.map { x =>
            x.copy(
              userName = newUserName,
              repositoryName = newRepositoryName,
              milestoneId = x.milestoneId.map { id =>
                newMilestones.find(_.title == milestones.find(_.milestoneId == id).get.title).get.milestoneId
              },
              priorityId = x.priorityId.map { id =>
                newPriorities
                  .find(_.priorityName == priorities.find(_.priorityId == id).get.priorityName)
                  .get
                  .priorityId
              }
            )
          }: _*)

          PullRequests.insertAll(
            pullRequests.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )
          IssueComments.insertAll(
            issueComments.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )
          Labels.insertAll(labels.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*)
          CommitComments.insertAll(
            commitComments.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )
          CommitStatuses.insertAll(
            commitStatuses.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )
          ProtectedBranches.insertAll(
            protectedBranches.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )
          ProtectedBranchContexts.insertAll(
            protectedBranchContexts.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )
          DeployKeys.insertAll(deployKeys.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*)
          ReleaseTags.insertAll(releases.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*)
          ReleaseAssets.insertAll(
            releaseAssets.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )

          // Update source repository of pull requests
          PullRequests
            .filter { t =>
              (t.requestUserName === oldUserName.bind) && (t.requestRepositoryName === oldRepositoryName.bind)
            }
            .map { t =>
              t.requestUserName -> t.requestRepositoryName
            }
            .update(newUserName, newRepositoryName)

          // Convert labelId
          val oldLabelMap = labels.map(x => (x.labelId, x.labelName)).toMap
          val newLabelMap =
            Labels.filter(_.byRepository(newUserName, newRepositoryName)).map(x => (x.labelName, x.labelId)).list.toMap
          IssueLabels.insertAll(
            issueLabels.map(
              x =>
                x.copy(
                  labelId = newLabelMap(oldLabelMap(x.labelId)),
                  userName = newUserName,
                  repositoryName = newRepositoryName
              )
            ): _*
          )

          // TODO Drop transferred owner from collaborators?
          Collaborators.insertAll(
            collaborators.map(_.copy(userName = newUserName, repositoryName = newRepositoryName)): _*
          )

          // Move git repository
          defining(getRepositoryDir(oldUserName, oldRepositoryName)) { dir =>
            if (dir.isDirectory) {
              FileUtils.moveDirectory(dir, getRepositoryDir(newUserName, newRepositoryName))
            }
          }
          // Move wiki repository
          defining(getWikiRepositoryDir(oldUserName, oldRepositoryName)) { dir =>
            if (dir.isDirectory) {
              FileUtils.moveDirectory(dir, getWikiRepositoryDir(newUserName, newRepositoryName))
            }
          }
          // Move files directory
          defining(getRepositoryFilesDir(oldUserName, oldRepositoryName)) { dir =>
            if (dir.isDirectory) {
              FileUtils.moveDirectory(dir, getRepositoryFilesDir(newUserName, newRepositoryName))
            }
          }
          // Delete parent directory
          FileUtil.deleteDirectoryIfEmpty(getRepositoryFilesDir(oldUserName, oldRepositoryName))

          // Call hooks
          if (oldUserName == newUserName) {
            PluginRegistry().getRepositoryHooks.foreach(_.renamed(oldUserName, oldRepositoryName, newRepositoryName))
          } else {
            PluginRegistry().getRepositoryHooks.foreach(_.transferred(oldUserName, newUserName, newRepositoryName))
          }
        }
      }
    }
  }

  def deleteRepository(repository: Repository)(implicit s: Session): Unit = {
    LockUtil.lock(s"${repository.userName}/${repository.repositoryName}") {
      deleteRepositoryOnModel(repository.userName, repository.repositoryName)

      FileUtils.deleteDirectory(getRepositoryDir(repository.userName, repository.repositoryName))
      FileUtils.deleteDirectory(getWikiRepositoryDir(repository.userName, repository.repositoryName))
      FileUtils.deleteDirectory(getTemporaryDir(repository.userName, repository.repositoryName))
      FileUtils.deleteDirectory(getRepositoryFilesDir(repository.userName, repository.repositoryName))

      // Call hooks
      PluginRegistry().getRepositoryHooks.foreach(_.deleted(repository.userName, repository.repositoryName))
    }
  }

  private def deleteRepositoryOnModel(userName: String, repositoryName: String)(implicit s: Session): Unit = {
//    Activities.filter(_.byRepository(userName, repositoryName)).delete
    Collaborators.filter(_.byRepository(userName, repositoryName)).delete
    CommitComments.filter(_.byRepository(userName, repositoryName)).delete
    IssueLabels.filter(_.byRepository(userName, repositoryName)).delete
    Labels.filter(_.byRepository(userName, repositoryName)).delete
    IssueComments.filter(_.byRepository(userName, repositoryName)).delete
    PullRequests.filter(_.byRepository(userName, repositoryName)).delete
    Issues.filter(_.byRepository(userName, repositoryName)).delete
    Priorities.filter(_.byRepository(userName, repositoryName)).delete
    IssueId.filter(_.byRepository(userName, repositoryName)).delete
    Milestones.filter(_.byRepository(userName, repositoryName)).delete
    RepositoryWebHooks.filter(_.byRepository(userName, repositoryName)).delete
    RepositoryWebHookEvents.filter(_.byRepository(userName, repositoryName)).delete
    DeployKeys.filter(_.byRepository(userName, repositoryName)).delete
    ReleaseAssets.filter(_.byRepository(userName, repositoryName)).delete
    ReleaseTags.filter(_.byRepository(userName, repositoryName)).delete
    Repositories.filter(_.byRepository(userName, repositoryName)).delete

    // Update ORIGIN_USER_NAME and ORIGIN_REPOSITORY_NAME
    Repositories
      .filter { x =>
        (x.originUserName === userName.bind) && (x.originRepositoryName === repositoryName.bind)
      }
      .map { x =>
        (x.userName, x.repositoryName)
      }
      .list
      .foreach {
        case (userName, repositoryName) =>
          Repositories
            .filter(_.byRepository(userName, repositoryName))
            .map(x => (x.originUserName ?, x.originRepositoryName ?))
            .update(None, None)
      }

    // Update PARENT_USER_NAME and PARENT_REPOSITORY_NAME
    Repositories
      .filter { x =>
        (x.parentUserName === userName.bind) && (x.parentRepositoryName === repositoryName.bind)
      }
      .map { x =>
        (x.userName, x.repositoryName)
      }
      .list
      .foreach {
        case (userName, repositoryName) =>
          Repositories
            .filter(_.byRepository(userName, repositoryName))
            .map(x => (x.parentUserName ?, x.parentRepositoryName ?))
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
    Repositories filter (_.userName === userName.bind) map (_.repositoryName) list

  /**
   * Returns the specified repository information.
   *
   * @param userName the user name of the repository owner
   * @param repositoryName the repository name
   * @return the repository information
   */
  def getRepository(userName: String, repositoryName: String)(implicit s: Session): Option[RepositoryInfo] = {
    (Repositories
      .join(Accounts)
      .on(_.userName === _.userName)
      .filter {
        case (t1, t2) =>
          t1.byRepository(userName, repositoryName) && t2.removed === false.bind
    } firstOption) map {
      case (repository, account) =>
        // for getting issue count and pull request count
        val issues = Issues
          .filter { t =>
            t.byRepository(repository.userName, repository.repositoryName) && (t.closed === false.bind)
          }
          .map(_.pullRequest)
          .list

        new RepositoryInfo(
          JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName),
          repository,
          issues.count(_ == false),
          issues.count(_ == true),
          getForkedCount(
            repository.originUserName.getOrElse(repository.userName),
            repository.originRepositoryName.getOrElse(repository.repositoryName)
          ),
          getOpenMilestones(
            repository.originUserName.getOrElse(repository.userName),
            repository.originRepositoryName.getOrElse(repository.repositoryName)
          ),
          getRepositoryManagers(repository.userName, repository.repositoryName)
        )
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
    Repositories
      .filter { t1 =>
        (t1.isPrivate === false.bind) ||
        (t1.userName === userName.bind) || (t1.userName in (GroupMembers
          .filter(_.userName === userName.bind)
          .map(_.groupName))) ||
        (Collaborators.filter { t2 =>
          t2.byRepository(t1.userName, t1.repositoryName) &&
          ((t2.collaboratorName === userName.bind) || (t2.collaboratorName in GroupMembers
            .filter(_.userName === userName.bind)
            .map(_.groupName)))
        } exists)
      }
      .sortBy(_.lastActivityDate desc)
      .map { t =>
        (t.userName, t.repositoryName)
      }
      .list
  }

  /**
   * Returns the all public repositories.
   *
   * @return the repository information list
   */
  def getPublicRepositories(withoutPhysicalInfo: Boolean = false)(implicit s: Session): List[RepositoryInfo] = {
    Repositories
      .filter { t1 =>
        t1.isPrivate === false.bind
      }
      .sortBy(_.lastActivityDate desc)
      .list
      .map(createRepositoryInfo(_, withoutPhysicalInfo))
  }

  /**
   * Returns the list of repositories which are owned by the specified user.
   * This list includes group repositories if the specified user is a member of the group.
   */
  def getUserRepositories(userName: String, withoutPhysicalInfo: Boolean = false)(
    implicit s: Session
  ): List[RepositoryInfo] = {
    Repositories
      .filter { t1 =>
        (t1.userName === userName.bind) || (t1.userName in (GroupMembers
          .filter(_.userName === userName.bind)
          .map(_.groupName))) ||
        (Collaborators.filter { t2 =>
          t2.byRepository(t1.userName, t1.repositoryName) &&
          ((t2.collaboratorName === userName.bind) || (t2.collaboratorName in GroupMembers
            .filter(_.userName === userName.bind)
            .map(_.groupName)))
        } exists)
      }
      .sortBy(_.lastActivityDate desc)
      .list
      .map(createRepositoryInfo(_, withoutPhysicalInfo))
  }

  /**
   * Returns the list of visible repositories for the specified user.
   * If repositoryUserName is given then filters results by repository owner.
   * This function is for plugin compatibility.
   *
   * @param loginAccount the logged in account
   * @param repositoryUserName the repository owner (if None then returns all repositories which are visible for logged in user)
   * @param withoutPhysicalInfo if true then the result does not include physical repository information such as commit count,
   *                            branches and tags
   * @return the repository information which is sorted in descending order of lastActivityDate.
   */
  def getVisibleRepositories(
    loginAccount: Option[Account],
    repositoryUserName: Option[String] = None,
    withoutPhysicalInfo: Boolean = false
  )(implicit s: Session): List[RepositoryInfo] =
    getVisibleRepositories(loginAccount, repositoryUserName, withoutPhysicalInfo, false)

  /**
   * Returns the list of visible repositories for the specified user.
   * If repositoryUserName is given then filters results by repository owner.
   *
   * @param loginAccount the logged in account
   * @param repositoryUserName the repository owner (if None then returns all repositories which are visible for logged in user)
   * @param withoutPhysicalInfo if true then the result does not include physical repository information such as commit count,
   *                            branches and tags
   * @param limit if true then result will include only repositories owned by the login account. otherwise, result will be all visible repositories.
   * @return the repository information which is sorted in descending order of lastActivityDate.
   */
  def getVisibleRepositories(
    loginAccount: Option[Account],
    repositoryUserName: Option[String],
    withoutPhysicalInfo: Boolean,
    limit: Boolean
  )(implicit s: Session): List[RepositoryInfo] = {
    (loginAccount match {
      // for Administrators
      case Some(x) if (x.isAdmin && !limit) =>
        Repositories
          .join(Accounts)
          .on(_.userName === _.userName)
          .filter { case (t1, t2) => t2.removed === false.bind }
          .map { case (t1, t2) => t1 }
      // for Normal Users
      case Some(x) if (!x.isAdmin || limit) =>
        Repositories
          .join(Accounts)
          .on(_.userName === _.userName)
          .filter {
            case (t1, t2) =>
              (t2.removed === false.bind) && ((t1.isPrivate === false.bind && !limit.bind) || (t1.userName === x.userName) ||
                (t1.userName in GroupMembers.filter(_.userName === x.userName.bind).map(_.groupName)) ||
                (Collaborators.filter { t3 =>
                  t3.byRepository(t1.userName, t1.repositoryName) &&
                  ((t3.collaboratorName === x.userName.bind) ||
                  (t3.collaboratorName in GroupMembers.filter(_.userName === x.userName.bind).map(_.groupName)))
                } exists))
          }
          .map { case (t1, t2) => t1 }
      // for Guests
      case None =>
        Repositories
          .join(Accounts)
          .on(_.userName === _.userName)
          .filter { case (t1, t2) => t1.isPrivate === false.bind && t2.removed === false.bind }
          .map { case (t1, t2) => t1 }
    }).filter { t =>
        repositoryUserName.map { userName =>
          t.userName === userName.bind
        } getOrElse LiteralColumn(true)
      }
      .sortBy(_.lastActivityDate desc)
      .list
      .map(createRepositoryInfo(_, withoutPhysicalInfo))
  }

  private def createRepositoryInfo(repository: Repository, withoutPhysicalInfo: Boolean = false)(
    implicit s: Session
  ): RepositoryInfo = {
    new RepositoryInfo(
      if (withoutPhysicalInfo) {
        new JGitUtil.RepositoryInfo(repository.userName, repository.repositoryName)
      } else {
        JGitUtil.getRepositoryInfo(repository.userName, repository.repositoryName)
      },
      repository,
      if (withoutPhysicalInfo) {
        -1
      } else {
        getForkedCount(
          repository.originUserName.getOrElse(repository.userName),
          repository.originRepositoryName.getOrElse(repository.repositoryName)
        )
      },
      if (withoutPhysicalInfo) {
        Nil
      } else {
        getRepositoryManagers(repository.userName, repository.repositoryName)
      }
    )
  }

  /**
   * TODO It seems to be able to improve performance. For example, RequestCache can be used for getAccountByUserName call.
   */
  private def getRepositoryManagers(userName: String, repositoryName: String)(implicit s: Session): Seq[String] = {
    (if (getAccountByUserName(userName).exists(_.isGroupAccount)) {
       getGroupMembers(userName).collect { case x if (x.isManager) => x.userName }
     } else {
       Seq(userName)
     }) ++ getCollaboratorUserNames(userName, repositoryName, Seq(Role.ADMIN))
  }

  /**
   * Updates the last activity date of the repository.
   */
  def updateLastActivityDate(userName: String, repositoryName: String)(implicit s: Session): Unit = {
    Repositories.filter(_.byRepository(userName, repositoryName)).map(_.lastActivityDate).update(currentDate)
  }

  /**
   * Save repository options.
   */
  def saveRepositoryOptions(
    userName: String,
    repositoryName: String,
    description: Option[String],
    isPrivate: Boolean,
    issuesOption: String,
    externalIssuesUrl: Option[String],
    wikiOption: String,
    externalWikiUrl: Option[String],
    allowFork: Boolean,
    mergeOptions: Seq[String],
    defaultMergeOption: String
  )(implicit s: Session): Unit = {

    Repositories
      .filter(_.byRepository(userName, repositoryName))
      .map { r =>
        (
          r.description.?,
          r.isPrivate,
          r.issuesOption,
          r.externalIssuesUrl.?,
          r.wikiOption,
          r.externalWikiUrl.?,
          r.allowFork,
          r.mergeOptions,
          r.defaultMergeOption,
          r.updatedDate
        )
      }
      .update(
        description,
        isPrivate,
        issuesOption,
        externalIssuesUrl,
        wikiOption,
        externalWikiUrl,
        allowFork,
        mergeOptions.mkString(","),
        defaultMergeOption,
        currentDate
      )
  }

  def saveRepositoryDefaultBranch(userName: String, repositoryName: String, defaultBranch: String)(
    implicit s: Session
  ): Unit =
    Repositories
      .filter(_.byRepository(userName, repositoryName))
      .map { r =>
        r.defaultBranch
      }
      .update(defaultBranch)

  /**
   * Add collaborator (user or group) to the repository.
   */
  def addCollaborator(userName: String, repositoryName: String, collaboratorName: String, role: String)(
    implicit s: Session
  ): Unit =
    Collaborators insert Collaborator(userName, repositoryName, collaboratorName, role)

  /**
   * Remove specified collaborator from the repository.
   */
  def removeCollaborator(userName: String, repositoryName: String, collaboratorName: String)(
    implicit s: Session
  ): Unit =
    Collaborators.filter(_.byPrimaryKey(userName, repositoryName, collaboratorName)).delete

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
      .join(Accounts)
      .on(_.collaboratorName === _.userName)
      .filter { case (t1, t2) => t1.byRepository(userName, repositoryName) }
      .map { case (t1, t2) => (t1, t2.groupAccount) }
      .sortBy { case (t1, t2) => t1.collaboratorName }
      .list

  /**
   * Returns the list of all collaborator name and permission which is sorted with ascending order.
   * If a group is added as a collaborator, this method returns users who are belong to that group.
   */
  def getCollaboratorUserNames(userName: String, repositoryName: String, filter: Seq[Role] = Nil)(
    implicit s: Session
  ): List[String] = {
    val q1 = Collaborators
      .join(Accounts)
      .on { case (t1, t2) => (t1.collaboratorName === t2.userName) && (t2.groupAccount === false.bind) }
      .filter { case (t1, t2) => t1.byRepository(userName, repositoryName) }
      .map { case (t1, t2) => (t1.collaboratorName, t1.role) }

    val q2 = Collaborators
      .join(Accounts)
      .on { case (t1, t2) => (t1.collaboratorName === t2.userName) && (t2.groupAccount === true.bind) }
      .join(GroupMembers)
      .on { case ((t1, t2), t3) => t2.userName === t3.groupName }
      .filter { case ((t1, t2), t3) => t1.byRepository(userName, repositoryName) }
      .map { case ((t1, t2), t3) => (t3.userName, t1.role) }

    q1.union(q2)
      .list
      .filter { x =>
        filter.isEmpty || filter.exists(_.name == x._2)
      }
      .map(_._1)
  }

  def hasOwnerRole(owner: String, repository: String, loginAccount: Option[Account])(implicit s: Session): Boolean = {
    loginAccount match {
      case Some(a) if (a.isAdmin)                                                                         => true
      case Some(a) if (a.userName == owner)                                                               => true
      case Some(a) if (getGroupMembers(owner).exists(_.userName == a.userName))                           => true
      case Some(a) if (getCollaboratorUserNames(owner, repository, Seq(Role.ADMIN)).contains(a.userName)) => true
      case _                                                                                              => false
    }
  }

  def hasDeveloperRole(owner: String, repository: String, loginAccount: Option[Account])(
    implicit s: Session
  ): Boolean = {
    loginAccount match {
      case Some(a) if (a.isAdmin)                                               => true
      case Some(a) if (a.userName == owner)                                     => true
      case Some(a) if (getGroupMembers(owner).exists(_.userName == a.userName)) => true
      case Some(a)
          if (getCollaboratorUserNames(owner, repository, Seq(Role.ADMIN, Role.DEVELOPER)).contains(a.userName)) =>
        true
      case _ => false
    }
  }

  def hasGuestRole(owner: String, repository: String, loginAccount: Option[Account])(implicit s: Session): Boolean = {
    loginAccount match {
      case Some(a) if (a.isAdmin)                                               => true
      case Some(a) if (a.userName == owner)                                     => true
      case Some(a) if (getGroupMembers(owner).exists(_.userName == a.userName)) => true
      case Some(a)
          if (getCollaboratorUserNames(owner, repository, Seq(Role.ADMIN, Role.DEVELOPER, Role.GUEST))
            .contains(a.userName)) =>
        true
      case _ => false
    }
  }

  def isReadable(repository: Repository, loginAccount: Option[Account])(implicit s: Session): Boolean = {
    if (!repository.isPrivate) {
      true
    } else {
      loginAccount match {
        case Some(x) if (x.isAdmin)                                                             => true
        case Some(x) if (repository.userName == x.userName)                                     => true
        case Some(x) if (getGroupMembers(repository.userName).exists(_.userName == x.userName)) => true
        case Some(x)
            if (getCollaboratorUserNames(repository.userName, repository.repositoryName).contains(x.userName)) =>
          true
        case _ => false
      }
    }
  }

  private def getForkedCount(userName: String, repositoryName: String)(implicit s: Session): Int =
    Query(Repositories.filter { t =>
      (t.originUserName === userName.bind) && (t.originRepositoryName === repositoryName.bind)
    }.length).first

  private def getOpenMilestones(userName: String, repositoryName: String)(implicit s: Session): Int =
    Query(
      Milestones
        .filter(_.byRepository(userName, repositoryName))
        .filter(_.closedDate.isEmpty)
        .length
    ).first

  def getForkedRepositories(userName: String, repositoryName: String)(implicit s: Session): List[Repository] =
    Repositories
      .filter { t =>
        (t.originUserName === userName.bind) && (t.originRepositoryName === repositoryName.bind)
      }
      .sortBy(_.userName asc)
      .list //.map(t => t.userName -> t.repositoryName).list

  private val templateExtensions = Seq("md", "markdown")

  /**
   * Returns content of template set per repository.
   *
   * @param repository the repository information
   * @param fileBaseName the file basename without extension of template
   * @return The content of template if the repository has it, otherwise empty string.
   */
  def getContentTemplate(repository: RepositoryInfo, fileBaseName: String)(implicit s: Session): String = {
    val withExtFilenames = templateExtensions.map(extension => s"${fileBaseName.toLowerCase()}.${extension}")

    def choiceTemplate(files: List[FileInfo]): Option[FileInfo] =
      files
        .find { f =>
          f.name.toLowerCase() == fileBaseName
        }
        .orElse {
          files.find(f => withExtFilenames.contains(f.name.toLowerCase()))
        }

    // Get template file from project root. When didn't find, will lookup default folder.
    Using.resource(Git.open(Directory.getRepositoryDir(repository.owner, repository.name))) { git =>
      // maxFiles = 1 means not get commit info because the only objectId and filename are necessary here
      choiceTemplate(JGitUtil.getFileList(git, repository.repository.defaultBranch, ".", maxFiles = 1))
        .orElse {
          choiceTemplate(JGitUtil.getFileList(git, repository.repository.defaultBranch, ".gitbucket", maxFiles = 1))
        }
        .map { file =>
          JGitUtil.getContentFromId(git, file.id, true).collect {
            case bytes if FileUtil.isText(bytes) => StringUtil.convertFromByteArray(bytes)
          }
        } getOrElse None
    } getOrElse ""
  }
}

object RepositoryService {
  case class RepositoryInfo(
    owner: String,
    name: String,
    repository: Repository,
    issueCount: Int,
    pullCount: Int,
    forkedCount: Int,
    milestoneCount: Int,
    branchList: Seq[String],
    tags: Seq[JGitUtil.TagInfo],
    managers: Seq[String]
  ) {

    /**
     * Creates instance with issue count and pull request count.
     */
    def this(
      repo: JGitUtil.RepositoryInfo,
      model: Repository,
      issueCount: Int,
      pullCount: Int,
      forkedCount: Int,
      milestoneCount: Int,
      managers: Seq[String]
    ) =
      this(
        repo.owner,
        repo.name,
        model,
        issueCount,
        pullCount,
        forkedCount,
        milestoneCount,
        repo.branchList,
        repo.tags,
        managers
      )

    /**
     * Creates instance without issue, pull request, and milestone count.
     */
    def this(repo: JGitUtil.RepositoryInfo, model: Repository, forkedCount: Int, managers: Seq[String]) =
      this(repo.owner, repo.name, model, 0, 0, forkedCount, 0, repo.branchList, repo.tags, managers)

    def httpUrl(implicit context: Context): String = RepositoryService.httpUrl(owner, name)
    def sshUrl(implicit context: Context): Option[String] = RepositoryService.sshUrl(owner, name)

    def splitPath(path: String): (String, String) = {
      val id = branchList.collectFirst {
        case branch if (path == branch || path.startsWith(branch + "/")) => branch
      } orElse tags.collectFirst {
        case tag if (path == tag.name || path.startsWith(tag.name + "/")) => tag.name
      } getOrElse path.split("/")(0)

      (id, path.substring(id.length).stripPrefix("/"))
    }
  }

  def httpUrl(owner: String, name: String)(implicit context: Context): String =
    s"${context.baseUrl}/git/${owner}/${name}.git"
  def sshUrl(owner: String, name: String)(implicit context: Context): Option[String] =
    if (context.settings.ssh.enabled) {
      context.settings.sshAddress.map { x =>
        s"ssh://${x.genericUser}@${x.host}:${x.port}/${owner}/${name}.git"
      }
    } else None
  def openRepoUrl(openUrl: String)(implicit context: Context): String =
    s"github-${context.platform}://openRepo/${openUrl}"

  def readmeFiles: Seq[String] =
    PluginRegistry().renderableExtensions.map { extension =>
      s"readme.${extension}"
    } ++ Seq("readme.txt", "readme")
}
