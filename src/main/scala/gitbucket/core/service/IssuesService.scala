package gitbucket.core.service

import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.StringUtil._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.controller.Context
import gitbucket.core.model.{
  Account,
  Issue,
  IssueAssignee,
  IssueComment,
  IssueLabel,
  Label,
  Profile,
  PullRequest,
  Repository,
  Role
}
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile.dateColumnType
import gitbucket.core.plugin.PluginRegistry

import scala.jdk.CollectionConverters._

trait IssuesService {
  self: AccountService & RepositoryService & LabelsService & PrioritiesService & MilestonesService =>
  import IssuesService._

  def getIssue(owner: String, repository: String, issueId: String)(implicit s: Session) =
    if (isInteger(issueId))
      Issues filter (_.byPrimaryKey(owner, repository, issueId.toInt)) firstOption
    else None

  def getOpenIssues(owner: String, repository: String)(implicit s: Session): List[Issue] =
    Issues filter (_.byRepository(owner, repository)) filterNot (_.closed) sortBy (_.issueId desc) list

  def getComments(owner: String, repository: String, issueId: Int)(implicit s: Session) =
    IssueComments filter (_.byIssue(owner, repository, issueId)) sortBy (_.commentId asc) list

  /** @return IssueComment and commentedUser and Issue */
  def getCommentsForApi(owner: String, repository: String, issueId: Int)(implicit
    s: Session
  ): List[(IssueComment, Account, Issue)] =
    IssueComments
      .filter(_.byIssue(owner, repository, issueId))
      .filter(_.action inSetBind Set("comment", "close_comment", "reopen_comment"))
      .join(Accounts)
      .on { case t1 ~ t2 => t1.commentedUserName === t2.userName }
      .join(Issues)
      .on { case t1 ~ t2 ~ t3 => t3.byIssue(t1.userName, t1.repositoryName, t1.issueId) }
      .map { case t1 ~ t2 ~ t3 => (t1, t2, t3) }
      .list

  def getMergedComment(owner: String, repository: String, issueId: Int)(implicit
    s: Session
  ): Option[(IssueComment, Account)] = {
    IssueComments
      .filter(_.byIssue(owner, repository, issueId))
      .filter(_.action === "merge".bind)
      .join(Accounts)
      .on { case t1 ~ t2 => t1.commentedUserName === t2.userName }
      .map { case t1 ~ t2 => (t1, t2) }
      .firstOption
  }

  def getComment(owner: String, repository: String, commentId: String)(implicit s: Session): Option[IssueComment] = {
    if (commentId forall (_.isDigit))
      IssueComments filter { t =>
        t.byPrimaryKey(commentId.toInt) && t.byRepository(owner, repository)
      } firstOption
    else None
  }

  def getCommentForApi(owner: String, repository: String, commentId: Int)(implicit
    s: Session
  ): Option[(IssueComment, Account, Issue)] =
    IssueComments
      .filter(_.byRepository(owner, repository))
      .filter(_.commentId === commentId)
      .filter(_.action inSetBind Set("comment", "close_comment", "reopen_comment"))
      .join(Accounts)
      .on { case t1 ~ t2 => t1.commentedUserName === t2.userName }
      .join(Issues)
      .on { case t1 ~ t2 ~ t3 => t3.byIssue(t1.userName, t1.repositoryName, t1.issueId) }
      .map { case t1 ~ t2 ~ t3 => (t1, t2, t3) }
      .firstOption

  def getIssueLabels(owner: String, repository: String, issueId: Int)(implicit s: Session): List[Label] = {
    IssueLabels
      .join(Labels)
      .on { case t1 ~ t2 =>
        t1.byLabel(t2.userName, t2.repositoryName, t2.labelId)
      }
      .filter { case t1 ~ t2 => t1.byIssue(owner, repository, issueId) }
      .map { case t1 ~ t2 => t2 }
      .list
  }

  def getIssueLabel(owner: String, repository: String, issueId: Int, labelId: Int)(implicit
    s: Session
  ): Option[IssueLabel] = {
    IssueLabels filter (_.byPrimaryKey(owner, repository, issueId, labelId)) firstOption
  }

  /**
   * Returns the count of the search result against  issues.
   *
   * @param condition the search condition
   * @param searchOption if true then counts only pull request, false then counts both of issue and pull request.
   * @param repos Tuple of the repository owner and the repository name
   * @return the count of the search result
   */
  def countIssue(condition: IssueSearchCondition, searchOption: IssueSearchOption, repos: (String, String)*)(implicit
    s: Session
  ): Int = {
    Query(searchIssueQuery(repos, condition, searchOption).length).first
  }

  /**
   * Returns the Map which contains issue count for each labels.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param condition the search condition
   * @return the Map which contains issue count for each labels (key is label name, value is issue count)
   */
  def countIssueGroupByLabels(
    owner: String,
    repository: String,
    condition: IssueSearchCondition
  )(implicit s: Session): Map[String, Int] = {

    searchIssueQuery(Seq(owner -> repository), condition.copy(labels = Set.empty), IssueSearchOption.Issues)
      .join(IssueLabels)
      .on { case t1 ~ t2 =>
        t1.byIssue(t2.userName, t2.repositoryName, t2.issueId)
      }
      .join(Labels)
      .on { case t1 ~ t2 ~ t3 =>
        t2.byLabel(t3.userName, t3.repositoryName, t3.labelId)
      }
      .groupBy { case t1 ~ t2 ~ t3 =>
        t3.labelName
      }
      .map { case (labelName, t) =>
        labelName -> t.length
      }
      .list
      .toMap
  }

  /**
   * Returns the Map which contains issue count for each priority.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param condition the search condition
   * @return the Map which contains issue count for each priority (key is priority name, value is issue count)
   */
  def countIssueGroupByPriorities(
    owner: String,
    repository: String,
    condition: IssueSearchCondition
  )(implicit s: Session): Map[String, Int] = {

    searchIssueQuery(Seq(owner -> repository), condition.copy(labels = Set.empty), IssueSearchOption.Issues)
      .join(Priorities)
      .on { case t1 ~ t2 =>
        t1.byPriority(t2.userName, t2.repositoryName, t2.priorityId)
      }
      .groupBy { case t1 ~ t2 =>
        t2.priorityName
      }
      .map { case (priorityName, t) =>
        priorityName -> t.length
      }
      .list
      .toMap
  }

  /**
   * Returns the search result against issues.
   *
   * @param condition the search condition
   * @param searchOption if true then returns only pull requests, false then returns only issues.
   * @param offset the offset for pagination
   * @param limit the limit for pagination
   * @param repos Tuple of the repository owner and the repository name
   * @return the search result (list of tuples which contain issue, labels and comment count)
   */
  def searchIssue(
    condition: IssueSearchCondition,
    searchOption: IssueSearchOption,
    offset: Int,
    limit: Int,
    repos: (String, String)*
  )(implicit s: Session): List[IssueInfo] = {
    // get issues and comment count and labels
    val result = searchIssueQueryBase(condition, searchOption, offset, limit, repos)
      .joinLeft(IssueLabels)
      .on { case t1 ~ t2 ~ i ~ t3 => t1.byIssue(t3.userName, t3.repositoryName, t3.issueId) }
      .joinLeft(Labels)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 => t3.map(_.byLabel(t4.userName, t4.repositoryName, t4.labelId)) }
      .joinLeft(Milestones)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 => t1.byMilestone(t5.userName, t5.repositoryName, t5.milestoneId) }
      .joinLeft(Priorities)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 => t1.byPriority(t6.userName, t6.repositoryName, t6.priorityId) }
      .joinLeft(PullRequests)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 => t1.byIssue(t7.userName, t7.repositoryName, t7.issueId) }
      .joinLeft(IssueAssignees)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 => t1.byIssue(t8.userName, t8.repositoryName, t8.issueId) }
      .sortBy { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 => i asc }
      .map { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 =>
        (
          t1,
          t2.commentCount,
          t4.map(_.labelId),
          t4.map(_.labelName),
          t4.map(_.color),
          t5.map(_.title),
          t6.map(_.priorityName),
          t7.map(_.commitIdTo),
          t8.map(_.assigneeUserName)
        )
      }
      .list
      .splitWith { (c1, c2) =>
        c1._1.userName == c2._1.userName && c1._1.repositoryName == c2._1.repositoryName && c1._1.issueId == c2._1.issueId
      }

    result.map { issues =>
      issues.head match {
        case (issue, commentCount, _, _, _, milestone, priority, commitId, _) =>
          IssueInfo(
            issue,
            issues
              .flatMap { t =>
                t._3.map(Label(issue.userName, issue.repositoryName, _, t._4.get, t._5.get))
              }
              .distinct
              .toList,
            milestone,
            priority,
            commentCount,
            commitId,
            issues.flatMap(_._9).distinct
          )
      }
    } toList
  }

  /** for api
   * @return (issue, issueUser, Seq(assigneeUsers))
   */
  def searchIssueByApi(condition: IssueSearchCondition, offset: Int, limit: Int, repos: (String, String)*)(implicit
    s: Session
  ): List[(Issue, Account, List[Account])] = {
    // get issues and comment count and labels
    searchIssueQueryBase(condition, IssueSearchOption.Issues, offset, limit, repos)
      .join(Accounts)
      .on { case t1 ~ t2 ~ i ~ t3 => t3.userName === t1.openedUserName }
      .joinLeft(IssueAssignees)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 => t4.byIssue(t1.userName, t1.repositoryName, t1.issueId) }
      .joinLeft(Accounts)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 => t5.userName === t4.map(_.assigneeUserName) }
      .sortBy { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 => i asc }
      .map { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 => (t1, t3, t5) }
      .list
      .groupBy { case (issue, account, _) =>
        (issue, account)
      }
      .map { case (_, values) =>
        (values.head._1, values.head._2, values.flatMap(_._3))
      }
      .toList
  }

  /** for api
   * @return (issue, issueUser, commentCount, pullRequest, headRepo, headOwner)
   */
  def searchPullRequestByApi(condition: IssueSearchCondition, offset: Int, limit: Int, repos: (String, String)*)(
    implicit s: Session
  ): List[(Issue, Account, Int, PullRequest, Repository, Account, List[Account])] = {
    // get issues and comment count and labels
    searchIssueQueryBase(condition, IssueSearchOption.PullRequests, offset, limit, repos)
      .join(PullRequests)
      .on { case t1 ~ t2 ~ i ~ t3 => t3.byPrimaryKey(t1.userName, t1.repositoryName, t1.issueId) }
      .join(Repositories)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 => t4.byRepository(t1.userName, t1.repositoryName) }
      .join(Accounts)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 => t5.userName === t1.openedUserName }
      .join(Accounts)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 => t6.userName === t4.userName }
      .joinLeft(IssueAssignees)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 => t7.byIssue(t1.userName, t1.repositoryName, t1.issueId) }
      .joinLeft(Accounts)
      .on { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 => t8.userName === t7.map(_.assigneeUserName) }
      .sortBy { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 => i asc }
      .map { case t1 ~ t2 ~ i ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 => (t1, t5, t2.commentCount, t3, t4, t6, t8) }
      .list
      .groupBy { case (issue, openedUser, commentCount, pullRequest, repository, account, assignedUser) =>
        (issue, openedUser, commentCount, pullRequest, repository, account)
      }
      .map { case (_, values) =>
        (
          values.head._1,
          values.head._2,
          values.head._3,
          values.head._4,
          values.head._5,
          values.head._6,
          values.flatMap(_._7)
        )
      }
      .toList
  }

  private def searchIssueQueryBase(
    condition: IssueSearchCondition,
    searchOption: IssueSearchOption,
    offset: Int,
    limit: Int,
    repos: Seq[(String, String)]
  )(implicit s: Session) =
    searchIssueQuery(repos, condition, searchOption)
      .join(IssueOutline)
      .on { (t1, t2) =>
        t1.byIssue(t2.userName, t2.repositoryName, t2.issueId)
      }
      .sortBy { case (t1, t2) => t1.issueId desc }
      .sortBy { case (t1, t2) =>
        condition.sort match {
          case "created" =>
            condition.direction match {
              case "asc"  => t1.registeredDate asc
              case "desc" => t1.registeredDate desc
            }
          case "comments" =>
            condition.direction match {
              case "asc"  => t2.commentCount asc
              case "desc" => t2.commentCount desc
            }
          case "updated" =>
            condition.direction match {
              case "asc"  => t1.updatedDate asc
              case "desc" => t1.updatedDate desc
            }
          case "priority" =>
            condition.direction match {
              case "asc"  => t2.priority asc
              case "desc" => t2.priority desc
            }
        }
      }
      .drop(offset)
      .take(limit)
      .zipWithIndex

  /**
   * Assembles query for conditional issue searching.
   */
  private def searchIssueQuery(
    repos: Seq[(String, String)],
    condition: IssueSearchCondition,
    searchOption: IssueSearchOption
  )(implicit
    s: Session
  ) = {
    val query = Issues filter { t1 =>
      (if (repos.sizeIs == 1) {
         t1.byRepository(repos.head._1, repos.head._2)
       } else {
         ((t1.userName ++ "/" ++ t1.repositoryName) inSetBind (repos.map { case (owner, repo) => s"$owner/$repo" }))
       }) &&
      (condition.state match {
        case "open"   => t1.closed === false
        case "closed" => t1.closed === true
        case _        => t1.closed === true || t1.closed === false
      }).&&(t1.milestoneId.? isEmpty, condition.milestone.contains(None))
        .&&(t1.priorityId.? isEmpty, condition.priority.contains(None))
        // .&&(t1.assignedUserName.? isEmpty, condition.assigned == Some(None))
        .&&(t1.openedUserName === condition.author.get.bind, condition.author.isDefined) &&
      (searchOption match {
        case IssueSearchOption.Issues       => t1.pullRequest === false
        case IssueSearchOption.PullRequests => t1.pullRequest === true
        case IssueSearchOption.Both         => t1.pullRequest === false || t1.pullRequest === true
      })
        // Milestone filter
        .&&(
          Milestones filter { t2 =>
            (t2.byPrimaryKey(t1.userName, t1.repositoryName, t1.milestoneId)) &&
            (t2.title === condition.milestone.get.get.bind)
          } exists,
          condition.milestone.flatten.isDefined
        )
        // Priority filter
        .&&(
          Priorities filter { t2 =>
            (t2.byPrimaryKey(t1.userName, t1.repositoryName, t1.priorityId)) &&
            (t2.priorityName === condition.priority.get.get.bind)
          } exists,
          condition.priority.flatten.isDefined
        )
        // Assignee filter
        .&&(
          IssueAssignees filter { a =>
            a.byIssue(t1.userName, t1.repositoryName, t1.issueId) &&
            a.assigneeUserName === condition.assigned.get.get.bind
          } exists,
          condition.assigned.flatten.isDefined
        )
        // Label filter
        .&&(
          IssueLabels filter { t2 =>
            (t2.byIssue(t1.userName, t1.repositoryName, t1.issueId)) &&
            (t2.labelId in
              (Labels filter { t3 =>
                (t3.byRepository(t1.userName, t1.repositoryName)) &&
                (t3.labelName inSetBind condition.labels)
              } map (_.labelId)))
          } exists,
          condition.labels.nonEmpty
        )
        // Visibility filter
        .&&(
          Repositories filter { t2 =>
            (t2.byRepository(t1.userName, t1.repositoryName)) &&
            (t2.isPrivate === condition.visibility.contains("private").bind)
          } exists,
          condition.visibility.nonEmpty
        )
        // Organization (group) filter
        .&&(t1.userName inSetBind condition.groups, condition.groups.nonEmpty)
        // Mentioned filter
        .&&(
          (t1.openedUserName === condition.mentioned.get.bind) || (IssueAssignees filter { t1 =>
            t1.byIssue(
              t1.userName,
              t1.repositoryName,
              t1.issueId
            ) && t1.assigneeUserName === condition.mentioned.get.bind
          } exists) ||
            (IssueComments filter { t2 =>
              (t2.byIssue(
                t1.userName,
                t1.repositoryName,
                t1.issueId
              )) && (t2.commentedUserName === condition.mentioned.get.bind)
            } exists),
          condition.mentioned.isDefined
        )
    }

    condition.others.foldLeft(query) { case (query, cond) =>
      def condQuery(f: Rep[String] => Rep[Boolean]): Query[Profile.Issues, Issue, Seq] = {
        query.filter { t1 =>
          IssueCustomFields
            .join(CustomFields)
            .on { (t2, t3) =>
              t2.userName === t3.userName && t2.repositoryName === t3.repositoryName && t2.fieldId === t3.fieldId
            }
            .filter { case (t2, t3) =>
              t1.byIssue(t2.userName, t2.repositoryName, t2.issueId) && t3.fieldName === cond.name.bind && f(
                t2.value
              )
            } exists
        }
      }
      cond.operator match {
        case "eq"  => condQuery(_ === cond.value.bind)
        case "lt"  => condQuery(_ < cond.value.bind)
        case "gt"  => condQuery(_ > cond.value.bind)
        case "lte" => condQuery(_ <= cond.value.bind)
        case "gte" => condQuery(_ >= cond.value.bind)
        case _     => throw new IllegalArgumentException("Unsupported operator")
      }
    }
  }

  def insertIssue(
    owner: String,
    repository: String,
    loginUser: String,
    title: String,
    content: Option[String],
    milestoneId: Option[Int],
    priorityId: Option[Int],
    isPullRequest: Boolean = false
  )(implicit s: Session): Int = {
    // next id number
    sql"SELECT ISSUE_ID + 1 FROM ISSUE_ID WHERE USER_NAME = $owner AND REPOSITORY_NAME = $repository FOR UPDATE"
      .as[Int]
      .firstOption
      .filter { id =>
        Issues insert Issue(
          owner,
          repository,
          id,
          loginUser,
          milestoneId,
          priorityId,
          title,
          content,
          false,
          currentDate,
          currentDate,
          isPullRequest
        )

        // increment issue id
        IssueId
          .filter(_.byPrimaryKey(owner, repository))
          .map(_.issueId)
          .update(id) > 0
      } get
  }

  def registerIssueLabel(owner: String, repository: String, issueId: Int, labelId: Int, insertComment: Boolean = false)(
    implicit
    context: Context,
    s: Session
  ): Int = {
    if (insertComment) {
      IssueComments insert IssueComment(
        userName = owner,
        repositoryName = repository,
        issueId = issueId,
        action = "add_label",
        commentedUserName = context.loginAccount.map(_.userName).getOrElse("Unknown user"),
        content = getLabel(owner, repository, labelId).map(_.labelName).getOrElse("Unknown label"),
        registeredDate = currentDate,
        updatedDate = currentDate
      )
    }
    IssueLabels insert IssueLabel(owner, repository, issueId, labelId)
  }

  def deleteIssueLabel(owner: String, repository: String, issueId: Int, labelId: Int, insertComment: Boolean = false)(
    implicit
    context: Context,
    s: Session
  ): Int = {
    if (insertComment) {
      IssueComments insert IssueComment(
        userName = owner,
        repositoryName = repository,
        issueId = issueId,
        action = "delete_label",
        commentedUserName = context.loginAccount.map(_.userName).getOrElse("Unknown user"),
        content = getLabel(owner, repository, labelId).map(_.labelName).getOrElse("Unknown label"),
        registeredDate = currentDate,
        updatedDate = currentDate
      )
    }
    IssueLabels filter (_.byPrimaryKey(owner, repository, issueId, labelId)) delete
  }

  def deleteAllIssueLabels(owner: String, repository: String, issueId: Int, insertComment: Boolean = false)(implicit
    context: Context,
    s: Session
  ): Int = {
    if (insertComment) {
      IssueComments insert IssueComment(
        userName = owner,
        repositoryName = repository,
        issueId = issueId,
        action = "delete_label",
        commentedUserName = context.loginAccount.map(_.userName).getOrElse("Unknown user"),
        content = "All labels",
        registeredDate = currentDate,
        updatedDate = currentDate
      )
    }
    IssueLabels filter (_.byIssue(owner, repository, issueId)) delete
  }

  def createComment(
    owner: String,
    repository: String,
    loginUser: String,
    issueId: Int,
    content: String,
    action: String
  )(implicit s: Session): Int = {
    Issues.filter(_.byPrimaryKey(owner, repository, issueId)).map(_.updatedDate).update(currentDate)
    IssueComments returning IssueComments.map(_.commentId) insert IssueComment(
      userName = owner,
      repositoryName = repository,
      issueId = issueId,
      action = action,
      commentedUserName = loginUser,
      content = content,
      registeredDate = currentDate,
      updatedDate = currentDate
    )
  }

  def updateIssue(owner: String, repository: String, issueId: Int, title: String, content: Option[String])(implicit
    s: Session
  ): Int = {
    Issues
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map { t =>
        (t.title, t.content.?, t.updatedDate)
      }
      .update(title, content, currentDate)
  }

  def changeIssueToPullRequest(owner: String, repository: String, issueId: Int)(implicit s: Session): Int = {
    Issues
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map { t =>
        t.pullRequest
      }
      .update(true)
  }

  def getIssueAssignees(owner: String, repository: String, issueId: Int)(implicit
    s: Session
  ): List[IssueAssignee] = {
    IssueAssignees.filter(_.byIssue(owner, repository, issueId)).sortBy(_.assigneeUserName).list
  }

  def registerIssueAssignee(
    owner: String,
    repository: String,
    issueId: Int,
    assigneeUserName: String,
    insertComment: Boolean = false
  )(implicit
    context: Context,
    s: Session
  ): Int = {
    val assigner = context.loginAccount.map(_.userName)
    if (insertComment) {
      IssueComments insert IssueComment(
        userName = owner,
        repositoryName = repository,
        issueId = issueId,
        action = "add_assignee",
        commentedUserName = assigner.getOrElse("Unknown user"),
        content = assigneeUserName,
        registeredDate = currentDate,
        updatedDate = currentDate
      )
    }
    for (issue <- getIssue(owner, repository, issueId.toString); repo <- getRepository(owner, repository)) {
      PluginRegistry().getIssueHooks.foreach(_.assigned(issue, repo, assigner, Some(assigneeUserName), None))
    }
    IssueAssignees insert IssueAssignee(owner, repository, issueId, assigneeUserName)
  }

  def deleteIssueAssignee(
    owner: String,
    repository: String,
    issueId: Int,
    assigneeUserName: String,
    insertComment: Boolean = false
  )(implicit
    context: Context,
    s: Session
  ): Int = {
    val assigner = context.loginAccount.map(_.userName)
    if (insertComment) {
      IssueComments insert IssueComment(
        userName = owner,
        repositoryName = repository,
        issueId = issueId,
        action = "delete_assignee",
        commentedUserName = assigner.getOrElse("Unknown user"),
        content = assigneeUserName,
        registeredDate = currentDate,
        updatedDate = currentDate
      )
    }

    // TODO Notify plugins of unassignment as doing in registerIssueAssignee()?

    IssueAssignees filter (_.byPrimaryKey(owner, repository, issueId, assigneeUserName)) delete
  }

  def deleteAllIssueAssignees(owner: String, repository: String, issueId: Int, insertComment: Boolean = false)(implicit
    context: Context,
    s: Session
  ): Int = {
    val assigner = context.loginAccount.map(_.userName)
    if (insertComment) {
      IssueComments insert IssueComment(
        userName = owner,
        repositoryName = repository,
        issueId = issueId,
        action = "delete_assign",
        commentedUserName = assigner.getOrElse("Unknown user"),
        content = "All assignees",
        registeredDate = currentDate,
        updatedDate = currentDate
      )
    }

    // TODO Notify plugins of unassignment as doing in registerIssueAssignee()?

    IssueAssignees filter (_.byIssue(owner, repository, issueId)) delete
  }

  def updateMilestoneId(
    owner: String,
    repository: String,
    issueId: Int,
    milestoneId: Option[Int],
    insertComment: Boolean = false
  )(implicit context: Context, s: Session): Int = {
    if (insertComment) {
      val oldMilestoneName = getIssue(owner, repository, s"${issueId}").get.milestoneId
        .map(getMilestone(owner, repository, _).map(_.title).getOrElse("Unknown milestone"))
        .getOrElse("No milestone")
      val milestoneName = milestoneId
        .map(getMilestone(owner, repository, _).map(_.title).getOrElse("Unknown milestone"))
        .getOrElse("No milestone")
      IssueComments insert IssueComment(
        userName = owner,
        repositoryName = repository,
        issueId = issueId,
        action = "change_milestone",
        commentedUserName = context.loginAccount.map(_.userName).getOrElse("Unknown user"),
        content = s"${oldMilestoneName}:${milestoneName}",
        registeredDate = currentDate,
        updatedDate = currentDate
      )
    }
    Issues
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map(t => (t.milestoneId ?, t.updatedDate))
      .update(milestoneId, currentDate)
  }

  def updatePriorityId(
    owner: String,
    repository: String,
    issueId: Int,
    priorityId: Option[Int],
    insertComment: Boolean = false
  )(implicit context: Context, s: Session): Int = {
    if (insertComment) {
      val oldPriorityName = getIssue(owner, repository, s"${issueId}").get.priorityId
        .map(getPriority(owner, repository, _).map(_.priorityName).getOrElse("Unknown priority"))
        .getOrElse("No priority")
      val priorityName = priorityId
        .map(getPriority(owner, repository, _).map(_.priorityName).getOrElse("Unknown priority"))
        .getOrElse("No priority")
      IssueComments insert IssueComment(
        userName = owner,
        repositoryName = repository,
        issueId = issueId,
        action = "change_priority",
        commentedUserName = context.loginAccount.map(_.userName).getOrElse("Unknown user"),
        content = s"${oldPriorityName}:${priorityName}",
        registeredDate = currentDate,
        updatedDate = currentDate
      )
    }
    Issues
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map(t => (t.priorityId ?, t.updatedDate))
      .update(priorityId, currentDate)
  }

  def updateComment(owner: String, repository: String, issueId: Int, commentId: Int, content: String)(implicit
    s: Session
  ): Int = {
    Issues.filter(_.byPrimaryKey(owner, repository, issueId)).map(_.updatedDate).update(currentDate)
    IssueComments.filter(_.byPrimaryKey(commentId)).map(t => (t.content, t.updatedDate)).update(content, currentDate)
  }

  def deleteComment(owner: String, repository: String, issueId: Int, commentId: Int)(implicit
    context: Context,
    s: Session
  ): Int = {
    Issues.filter(_.byPrimaryKey(owner, repository, issueId)).map(_.updatedDate).update(currentDate)
    IssueComments.filter(_.byPrimaryKey(commentId)).first match {
      case c if c.action == "reopen_comment" =>
        IssueComments.filter(_.byPrimaryKey(commentId)).map(t => (t.content, t.action)).update("Reopen", "reopen")
      case c if c.action == "close_comment" =>
        IssueComments.filter(_.byPrimaryKey(commentId)).map(t => (t.content, t.action)).update("Close", "close")
      case _ =>
        IssueComments.filter(_.byPrimaryKey(commentId)).delete
        IssueComments insert IssueComment(
          userName = owner,
          repositoryName = repository,
          issueId = issueId,
          action = "delete_comment",
          commentedUserName = context.loginAccount.map(_.userName).getOrElse("Unknown user"),
          content = s"",
          registeredDate = currentDate,
          updatedDate = currentDate
        )
    }
  }

  def updateClosed(owner: String, repository: String, issueId: Int, closed: Boolean)(implicit s: Session): Int = {
    Issues
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map(t => (t.closed, t.updatedDate))
      .update(closed, currentDate)
  }

  /**
   * Search issues by keyword.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param query the keywords separated by whitespace.
   * @return issues with comment count and matched content of issue or comment
   */
  def searchIssuesByKeyword(owner: String, repository: String, query: String, pullRequest: Boolean)(implicit
    s: Session
  ): List[(Issue, Int, String)] = {
    // import slick.driver.JdbcDriver.likeEncode
    val keywords = splitWords(query.toLowerCase)

    // Search Issue
    val issues = Issues
      .filter { t =>
        t.byRepository(owner, repository) && t.pullRequest === pullRequest.bind
      }
      .join(IssueOutline)
      .on { case (t1, t2) =>
        t1.byIssue(t2.userName, t2.repositoryName, t2.issueId)
      }
      .filter { case (t1, t2) =>
        keywords
          .map { keyword =>
            (t1.title.toLowerCase.like(s"%${likeEncode(keyword)}%", '^')) ||
            (t1.content.toLowerCase.like(s"%${likeEncode(keyword)}%", '^'))
          }
          .reduceLeft(_ && _)
      }
      .map { case (t1, t2) =>
        (t1, 0, t1.content.?, t2.commentCount)
      }

    // Search IssueComment
    val comments = IssueComments
      .filter(_.byRepository(owner, repository))
      .join(Issues)
      .on { case (t1, t2) =>
        t1.byIssue(t2.userName, t2.repositoryName, t2.issueId)
      }
      .join(IssueOutline)
      .on { case ((t1, t2), t3) =>
        t2.byIssue(t3.userName, t3.repositoryName, t3.issueId)
      }
      .filter { case ((t1, t2), t3) =>
        t2.pullRequest === pullRequest.bind &&
        keywords
          .map { query =>
            t1.content.toLowerCase.like(s"%${likeEncode(query)}%", '^')
          }
          .reduceLeft(_ && _)
      }
      .map { case ((t1, t2), t3) =>
        (t2, t1.commentId, t1.content.?, t3.commentCount)
      }

    issues
      .union(comments)
      .sortBy { case (issue, commentId, _, _) =>
        issue.issueId.desc -> commentId
      }
      .list
      .splitWith { case ((issue1, _, _, _), (issue2, _, _, _)) =>
        issue1.issueId == issue2.issueId
      }
      .map {
        _.head match {
          case (issue, _, content, commentCount) => (issue, commentCount, content.getOrElse(""))
        }
      }
      .toList
  }

  def closeIssuesFromMessage(message: String, userName: String, owner: String, repository: String)(implicit
    s: Session
  ): Seq[Int] = {
    extractCloseId(message).flatMap { issueId =>
      for (issue <- getIssue(owner, repository, issueId) if !issue.closed) yield {
        createComment(owner, repository, userName, issue.issueId, "Close", "close")
        updateClosed(owner, repository, issue.issueId, true)
        issue.issueId
      }
    }
  }

  def createReferComment(owner: String, repository: String, fromIssue: Issue, message: String, loginAccount: Account)(
    implicit s: Session
  ): Unit = {
    extractGlobalIssueId(message).foreach { case (_referredOwner, _referredRepository, referredIssueId) =>
      val referredOwner = _referredOwner.getOrElse(owner)
      val referredRepository = _referredRepository.getOrElse(repository)
      getRepository(referredOwner, referredRepository).foreach { repo =>
        if (isReadable(repo.repository, Option(loginAccount))) {
          getIssue(referredOwner, referredRepository, referredIssueId.get).foreach { _ =>
            val (content, action) = if (owner == referredOwner && repository == referredRepository) {
              (s"${fromIssue.issueId}:${fromIssue.title}", "refer")
            } else {
              (s"${fromIssue.issueId}:${owner}:${repository}:${fromIssue.title}", "refer_global")
            }
            referredIssueId.foreach(x =>
              // Not add if refer comment already exist.
              if (
                !getComments(referredOwner, referredRepository, x.toInt).exists { x =>
                  (x.action == "refer" || x.action == "refer_global") && x.content == content
                }
              ) {
                createComment(
                  referredOwner,
                  referredRepository,
                  loginAccount.userName,
                  x.toInt,
                  content,
                  action
                )
              }
            )
          }
        }
      }
    }
  }

  def createIssueComment(owner: String, repository: String, commit: CommitInfo)(implicit s: Session): Unit = {
    extractIssueId(commit.fullMessage).foreach { issueId =>
      if (getIssue(owner, repository, issueId).isDefined) {
        val userName =
          getAccountByMailAddress(commit.committerEmailAddress).map(_.userName).getOrElse(commit.committerName)
        createComment(owner, repository, userName, issueId.toInt, commit.fullMessage + " " + commit.id, "commit")
      }
    }
  }

  def getAssignableUserNames(owner: String, repository: String)(implicit s: Session): List[String] = {
    (getCollaboratorUserNames(owner, repository, Seq(Role.ADMIN, Role.DEVELOPER)) :::
      (getAccountByUserName(owner) match {
        case Some(x) if x.isGroupAccount =>
          getGroupMembers(owner).map(_.userName)
        case Some(_) =>
          List(owner)
        case None =>
          Nil
      })).distinct.sorted
  }

}

object IssuesService {
  import javax.servlet.http.HttpServletRequest

  val IssueLimit = 25

  case class CustomFieldCondition(name: String, value: String, operator: String)

  case class IssueSearchCondition(
    labels: Set[String] = Set.empty,
    milestone: Option[Option[String]] = None,
    priority: Option[Option[String]] = None,
    author: Option[String] = None,
    assigned: Option[Option[String]] = None,
    mentioned: Option[String] = None,
    state: String = "open",
    sort: String = "created",
    direction: String = "desc",
    visibility: Option[String] = None,
    groups: Set[String] = Set.empty,
    others: Seq[CustomFieldCondition] = Nil
  ) {
    def isEmpty: Boolean = {
      labels.isEmpty && milestone.isEmpty && author.isEmpty && assigned.isEmpty &&
      state == "open" && sort == "created" && direction == "desc" && visibility.isEmpty && others.isEmpty
    }

    def nonEmpty: Boolean = !isEmpty

    def toFilterString: String = if (isEmpty) ""
    else {
      (
        List(
          Some(s"is:${state}"),
          author.map(author => s"author:${author}"),
          assigned.map(assignee => s"assignee:${assignee}"),
          mentioned.map(mentioned => s"mentions:${mentioned}")
        ).flatten ++
          labels.map(label => s"label:${label}") ++
          List(
            milestone.map {
              case Some(x) => s"milestone:${x}"
              case None    => "no:milestone"
            },
            priority.map {
              case Some(x) => s"priority:${x}"
              case None    => "no:priority"
            },
            (sort, direction) match {
              case ("created", "desc")  => None
              case ("created", "asc")   => Some("sort:created-asc")
              case ("comments", "desc") => Some("sort:comments-desc")
              case ("comments", "asc")  => Some("sort:comments-asc")
              case ("updated", "desc")  => Some("sort:updated-desc")
              case ("updated", "asc")   => Some("sort:updated-asc")
              case ("priority", "desc") => Some("sort:priority-desc")
              case ("priority", "asc")  => Some("sort:priority-asc")
              case x                    => throw new MatchError(x)
            },
            visibility.map(visibility => s"visibility:${visibility}"),
          ).flatten ++
          others.map { cond =>
            cond.operator match {
              case "eq"  => s"custom.${cond.name}:${cond.value}"
              case "lt"  => s"custom.${cond.name}<${cond.value}"
              case "lte" => s"custom.${cond.name}<=${cond.value}"
              case "gt"  => s"custom.${cond.name}>${cond.value}"
              case "gte" => s"custom.${cond.name}>=${cond.value}"
            }
          } ++
          groups.map(group => s"group:${group}")
      ).mkString(" ")
    }

    def toURL: String = {
      "?" + (Seq(
        if (labels.isEmpty) None else Some("labels=" + urlEncode(labels.mkString(","))),
        milestone.map {
          case Some(x) => s"milestone=${urlEncode(x)}"
          case None    => "milestone=none"
        },
        priority.map {
          case Some(x) => s"priority=${urlEncode(x)}"
          case None    => "priority=none"
        },
        author.map(x => s"author=${urlEncode(x)}"),
        assigned.map {
          case Some(x) => s"assigned=${urlEncode(x)}"
          case None    => "assigned=none"
        },
        mentioned.map(x => s"mentioned=${urlEncode(x)}"),
        Some(s"state=${urlEncode(state)}"),
        Some(s"sort=${urlEncode(sort)}"),
        Some(s"direction=${urlEncode(direction)}"),
        visibility.map(x => s"visibility=${urlEncode(x)}"),
        if (groups.isEmpty) None else Some(s"groups=${urlEncode(groups.mkString(","))}")
      ).flatten ++ others.map { x =>
        s"custom.${urlEncode(x.name)}=${urlEncode(x.operator)}:${urlEncode(x.value)}"
      }).mkString("&")
    }
  }

  object IssueSearchCondition {

    private val SupportedOperators = Seq("eq", "lt", "gt", "lte", "gte")

    private def param(request: HttpServletRequest, name: String, allow: Seq[String] = Nil): Option[String] = {
      val value = request.getParameter(name)
      if (value == null || value.isEmpty || (allow.nonEmpty && !allow.contains(value))) None else Some(value)
    }

    /**
     * Restores IssueSearchCondition instance from filter query.
     */
    def apply(filter: String): IssueSearchCondition = {
      val conditions = filter
        .split("[ 　\t]+")
        .collect {
          case x if !x.startsWith("custom.") && x.indexOf(":") > 0 =>
            val dim = x.split(":")
            dim(0) -> dim(1)
        }
        .groupBy(_._1)
        .map { case (key, values) =>
          key -> values.map(_._2).toSeq
        }

      val (sort, direction) = conditions.get("sort").flatMap(_.headOption).getOrElse("created-desc") match {
        case "created-asc"   => ("created", "asc")
        case "comments-desc" => ("comments", "desc")
        case "comments-asc"  => ("comments", "asc")
        case "updated-desc"  => ("comments", "desc")
        case "updated-asc"   => ("comments", "asc")
        case _               => ("created", "desc")
      }

      val others = filter
        .split("[ 　\t]+")
        .collect {
          case x if x.startsWith("custom.") && x.indexOf(":") > 0 =>
            val dim = x.split(":")
            dim(0) -> ("eq", dim(1))
          case x if x.startsWith("custom.") && x.indexOf("<=") > 0 =>
            val dim = x.split("<=")
            dim(0) -> ("lte", dim(1))
          case x if x.startsWith("custom.") && x.indexOf("<") > 0 =>
            val dim = x.split("<")
            dim(0) -> ("lt", dim(1))
          case x if x.startsWith("custom.") && x.indexOf(">=") > 0 =>
            val dim = x.split(">=")
            dim(0) -> ("gte", dim(1))
          case x if x.startsWith("custom.") && x.indexOf(">") > 0 =>
            val dim = x.split(">")
            dim(0) -> ("gt", dim(1))
        }
        .map { case (key, (operator, value)) =>
          CustomFieldCondition(key.stripPrefix("custom."), value, operator)
        }
        .toSeq

      IssueSearchCondition(
        conditions.get("label").map(_.toSet).getOrElse(Set.empty),
        conditions.get("milestone").flatMap(_.headOption) match {
          case None         => None
          case Some("none") => Some(None)
          case Some(x)      => Some(Some(x)) // milestones.get(x).map(x => Some(x))
        },
        conditions.get("priority").map(_.headOption), // TODO
        conditions.get("author").flatMap(_.headOption),
        conditions.get("assignee").map(_.headOption), // TODO
        conditions.get("mentions").flatMap(_.headOption),
        conditions.get("is").getOrElse(Seq.empty).find(x => x == "open" || x == "closed").getOrElse("open"),
        sort,
        direction,
        conditions.get("visibility").flatMap(_.headOption),
        conditions.get("group").map(_.toSet).getOrElse(Set.empty),
        others
      )
    }

    /**
     * Restores IssueSearchCondition instance from request parameters.
     */
    def apply(request: HttpServletRequest): IssueSearchCondition = {
      val others = request.getParameterMap.asScala
        .collect {
          // custom.<field_name> = <operator>:<value>
          case (key, values) if key.startsWith("custom.") && values.nonEmpty && values.head.indexOf(":") > 0 =>
            val name = key.stripPrefix("custom.")
            val Array(operator, value) = values.head.split(":")
            CustomFieldCondition(name, value, operator)
          case (key, values) if key.startsWith("custom.") && values.nonEmpty =>
            val name = key.stripPrefix("custom.")
            CustomFieldCondition(name, values.head, "eq")
        }
        .filter { x =>
          SupportedOperators.contains(x.operator)
        }
        .toSeq

      IssueSearchCondition(
        param(request, "labels").map(_.split(",").toSet).getOrElse(Set.empty),
        param(request, "milestone").map {
          case "none" => None
          case x      => Some(x)
        },
        param(request, "priority").map {
          case "none" => None
          case x      => Some(x)
        },
        param(request, "author"),
        param(request, "assigned").map {
          case "none" => None
          case x      => Some(x)
        },
        param(request, "mentioned"),
        param(request, "state", Seq("open", "closed", "all")).getOrElse("open"),
        param(request, "sort", Seq("created", "comments", "updated", "priority")).getOrElse("created"),
        param(request, "direction", Seq("asc", "desc")).getOrElse("desc"),
        param(request, "visibility"),
        param(request, "groups").map(_.split(",").toSet).getOrElse(Set.empty),
        others
      )
    }

    def apply(request: HttpServletRequest, milestone: String): IssueSearchCondition = {
      apply(request).copy(milestone = Some(Some(milestone)))
    }

    def page(request: HttpServletRequest): Int = {
      PaginationHelper.page(param(request, "page"))
    }
  }

  case class IssueInfo(
    issue: Issue,
    labels: List[Label],
    milestone: Option[String],
    priority: Option[String],
    commentCount: Int,
    commitId: Option[String],
    assignees: Seq[String]
  )
}

sealed trait IssueSearchOption

object IssueSearchOption {
  case object Issues extends IssueSearchOption
  case object PullRequests extends IssueSearchOption
  case object Both extends IssueSearchOption
}
