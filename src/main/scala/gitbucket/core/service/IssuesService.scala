package gitbucket.core.service

import gitbucket.core.model._
import gitbucket.core.util.StringUtil._
import gitbucket.core.util.Implicits._
import scala.slick.jdbc.{StaticQuery => Q}
import Q.interpolation

import gitbucket.core.model.Profile._
import profile.simple._

trait IssuesService {
  import IssuesService._

  def getIssue(owner: String, repository: String, issueId: String)(implicit s: Session) =
    if (issueId forall (_.isDigit))
      Issues filter (_.byPrimaryKey(owner, repository, issueId.toInt)) firstOption
    else None

  def getComments(owner: String, repository: String, issueId: Int)(implicit s: Session) =
    IssueComments filter (_.byIssue(owner, repository, issueId)) list

  def getComment(owner: String, repository: String, commentId: String)(implicit s: Session) =
    if (commentId forall (_.isDigit))
      IssueComments filter { t =>
        t.byPrimaryKey(commentId.toInt) && t.byRepository(owner, repository)
      } firstOption
    else None

  def getIssueLabels(owner: String, repository: String, issueId: Int)(implicit s: Session) =
    IssueLabels
      .innerJoin(Labels).on { (t1, t2) =>
        t1.byLabel(t2.userName, t2.repositoryName, t2.labelId)
      }
      .filter ( _._1.byIssue(owner, repository, issueId) )
      .map    ( _._2 )
      .list

  def getIssueLabel(owner: String, repository: String, issueId: Int, labelId: Int)(implicit s: Session) =
    IssueLabels filter (_.byPrimaryKey(owner, repository, issueId, labelId)) firstOption

  /**
   * Returns the count of the search result against  issues.
   *
   * @param condition the search condition
   * @param onlyPullRequest if true then counts only pull request, false then counts both of issue and pull request.
   * @param repos Tuple of the repository owner and the repository name
   * @return the count of the search result
   */
  def countIssue(condition: IssueSearchCondition, onlyPullRequest: Boolean,
                 repos: (String, String)*)(implicit s: Session): Int =
    Query(searchIssueQuery(repos, condition, onlyPullRequest).length).first

  /**
   * Returns the Map which contains issue count for each labels.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param condition the search condition
   * @return the Map which contains issue count for each labels (key is label name, value is issue count)
   */
  def countIssueGroupByLabels(owner: String, repository: String, condition: IssueSearchCondition,
                              filterUser: Map[String, String])(implicit s: Session): Map[String, Int] = {

    searchIssueQuery(Seq(owner -> repository), condition.copy(labels = Set.empty), false)
      .innerJoin(IssueLabels).on { (t1, t2) =>
        t1.byIssue(t2.userName, t2.repositoryName, t2.issueId)
      }
      .innerJoin(Labels).on { case ((t1, t2), t3) =>
        t2.byLabel(t3.userName, t3.repositoryName, t3.labelId)
      }
      .groupBy { case ((t1, t2), t3) =>
        t3.labelName
      }
      .map { case (labelName, t) =>
        labelName -> t.length
      }
      .toMap
  }

  /**
   * Returns the search result against  issues.
   *
   * @param condition the search condition
   * @param pullRequest if true then returns only pull requests, false then returns only issues.
   * @param offset the offset for pagination
   * @param limit the limit for pagination
   * @param repos Tuple of the repository owner and the repository name
   * @return the search result (list of tuples which contain issue, labels and comment count)
   */
  def searchIssue(condition: IssueSearchCondition, pullRequest: Boolean, offset: Int, limit: Int, repos: (String, String)*)
                 (implicit s: Session): List[IssueInfo] = {

    // get issues and comment count and labels
    searchIssueQuery(repos, condition, pullRequest)
        .innerJoin(IssueOutline).on { (t1, t2) => t1.byIssue(t2.userName, t2.repositoryName, t2.issueId) }
        .sortBy { case (t1, t2) =>
          (condition.sort match {
            case "created"  => t1.registeredDate
            case "comments" => t2.commentCount
            case "updated"  => t1.updatedDate
          }) match {
            case sort => condition.direction match {
              case "asc"  => sort asc
              case "desc" => sort desc
            }
          }
        }
        .drop(offset).take(limit)
        .leftJoin (IssueLabels) .on { case ((t1, t2), t3) => t1.byIssue(t3.userName, t3.repositoryName, t3.issueId) }
        .leftJoin (Labels)      .on { case (((t1, t2), t3), t4) => t3.byLabel(t4.userName, t4.repositoryName, t4.labelId) }
        .leftJoin (Milestones)  .on { case ((((t1, t2), t3), t4), t5) => t1.byMilestone(t5.userName, t5.repositoryName, t5.milestoneId) }
        .map { case ((((t1, t2), t3), t4), t5) =>
          (t1, t2.commentCount, t4.labelId.?, t4.labelName.?, t4.color.?, t5.title.?)
        }
        .list
        .splitWith { (c1, c2) =>
          c1._1.userName       == c2._1.userName &&
          c1._1.repositoryName == c2._1.repositoryName &&
          c1._1.issueId        == c2._1.issueId
        }
        .map { issues => issues.head match {
          case (issue, commentCount, _, _, _, milestone) =>
            IssueInfo(issue,
             issues.flatMap { t => t._3.map (
                 Label(issue.userName, issue.repositoryName, _, t._4.get, t._5.get)
             )} toList,
             milestone,
             commentCount)
        }} toList
  }

  /**
   * Assembles query for conditional issue searching.
   */
  private def searchIssueQuery(repos: Seq[(String, String)], condition: IssueSearchCondition, pullRequest: Boolean)(implicit s: Session) =
    Issues filter { t1 =>
      repos
        .map { case (owner, repository) => t1.byRepository(owner, repository) }
        .foldLeft[Column[Boolean]](false) ( _ || _ ) &&
      (t1.closed           === (condition.state == "closed").bind) &&
      //(t1.milestoneId      === condition.milestoneId.get.get.bind, condition.milestoneId.flatten.isDefined) &&
      (t1.milestoneId.?    isEmpty, condition.milestone == Some(None)) &&
      (t1.assignedUserName === condition.assigned.get.bind, condition.assigned.isDefined) &&
      (t1.openedUserName   === condition.author.get.bind, condition.author.isDefined) &&
      (t1.pullRequest      === pullRequest.bind) &&
      // Milestone filter
      (Milestones filter { t2 =>
        (t2.byPrimaryKey(t1.userName, t1.repositoryName, t1.milestoneId)) &&
        (t2.title === condition.milestone.get.get.bind)
      } exists, condition.milestone.flatten.isDefined) &&
      // Label filter
      (IssueLabels filter { t2 =>
        (t2.byIssue(t1.userName, t1.repositoryName, t1.issueId)) &&
        (t2.labelId in
          (Labels filter { t3 =>
            (t3.byRepository(t1.userName, t1.repositoryName)) &&
            (t3.labelName inSetBind condition.labels)
          } map(_.labelId)))
      } exists, condition.labels.nonEmpty) &&
      // Visibility filter
      (Repositories filter { t2 =>
        (t2.byRepository(t1.userName, t1.repositoryName)) &&
        (t2.isPrivate === (condition.visibility == Some("private")).bind)
      } exists, condition.visibility.nonEmpty) &&
      // Organization (group) filter
      (t1.userName inSetBind condition.groups, condition.groups.nonEmpty) &&
      // Mentioned filter
      ((t1.openedUserName === condition.mentioned.get.bind) || t1.assignedUserName === condition.mentioned.get.bind ||
        (IssueComments filter { t2 =>
          (t2.byIssue(t1.userName, t1.repositoryName, t1.issueId)) && (t2.commentedUserName === condition.mentioned.get.bind)
        } exists), condition.mentioned.isDefined)
    }

  def createIssue(owner: String, repository: String, loginUser: String, title: String, content: Option[String],
                  assignedUserName: Option[String], milestoneId: Option[Int],
                  isPullRequest: Boolean = false)(implicit s: Session) =
    // next id number
    sql"SELECT ISSUE_ID + 1 FROM ISSUE_ID WHERE USER_NAME = $owner AND REPOSITORY_NAME = $repository FOR UPDATE".as[Int]
        .firstOption.filter { id =>
      Issues insert Issue(
          owner,
          repository,
          id,
          loginUser,
          milestoneId,
          assignedUserName,
          title,
          content,
          false,
          currentDate,
          currentDate,
          isPullRequest)

      // increment issue id
      IssueId
        .filter (_.byPrimaryKey(owner, repository))
        .map (_.issueId)
        .update (id) > 0
    } get

  def registerIssueLabel(owner: String, repository: String, issueId: Int, labelId: Int)(implicit s: Session) =
    IssueLabels insert IssueLabel(owner, repository, issueId, labelId)

  def deleteIssueLabel(owner: String, repository: String, issueId: Int, labelId: Int)(implicit s: Session) =
    IssueLabels filter(_.byPrimaryKey(owner, repository, issueId, labelId)) delete

  def createComment(owner: String, repository: String, loginUser: String,
      issueId: Int, content: String, action: String)(implicit s: Session): Int =
    IssueComments.autoInc insert IssueComment(
        userName          = owner,
        repositoryName    = repository,
        issueId           = issueId,
        action            = action,
        commentedUserName = loginUser,
        content           = content,
        registeredDate    = currentDate,
        updatedDate       = currentDate)

  def updateIssue(owner: String, repository: String, issueId: Int,
      title: String, content: Option[String])(implicit s: Session) =
    Issues
      .filter (_.byPrimaryKey(owner, repository, issueId))
      .map { t =>
        (t.title, t.content.?, t.updatedDate)
      }
      .update (title, content, currentDate)

  def updateAssignedUserName(owner: String, repository: String, issueId: Int,
                             assignedUserName: Option[String])(implicit s: Session) =
    Issues.filter (_.byPrimaryKey(owner, repository, issueId)).map(_.assignedUserName?).update (assignedUserName)

  def updateMilestoneId(owner: String, repository: String, issueId: Int,
                        milestoneId: Option[Int])(implicit s: Session) =
    Issues.filter (_.byPrimaryKey(owner, repository, issueId)).map(_.milestoneId?).update (milestoneId)

  def updateComment(commentId: Int, content: String)(implicit s: Session) =
    IssueComments
      .filter (_.byPrimaryKey(commentId))
      .map { t =>
        t.content -> t.updatedDate
      }
      .update (content, currentDate)

  def deleteComment(commentId: Int)(implicit s: Session) =
    IssueComments filter (_.byPrimaryKey(commentId)) delete

  def updateClosed(owner: String, repository: String, issueId: Int, closed: Boolean)(implicit s: Session) =
    Issues
      .filter (_.byPrimaryKey(owner, repository, issueId))
      .map { t =>
        t.closed -> t.updatedDate
      }
      .update (closed, currentDate)

  /**
   * Search issues by keyword.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param query the keywords separated by whitespace.
   * @return issues with comment count and matched content of issue or comment
   */
  def searchIssuesByKeyword(owner: String, repository: String, query: String)
                           (implicit s: Session): List[(Issue, Int, String)] = {
    import slick.driver.JdbcDriver.likeEncode
    val keywords = splitWords(query.toLowerCase)

    // Search Issue
    val issues = Issues
      .filter(_.byRepository(owner, repository))
      .innerJoin(IssueOutline).on { case (t1, t2) =>
        t1.byIssue(t2.userName, t2.repositoryName, t2.issueId)
      }
      .filter { case (t1, t2) =>
        keywords.map { keyword =>
          (t1.title.toLowerCase   like (s"%${likeEncode(keyword)}%", '^')) ||
          (t1.content.toLowerCase like (s"%${likeEncode(keyword)}%", '^'))
        } .reduceLeft(_ && _)
      }
      .map { case (t1, t2) =>
        (t1, 0, t1.content.?, t2.commentCount)
      }

    // Search IssueComment
    val comments = IssueComments
      .filter(_.byRepository(owner, repository))
      .innerJoin(Issues).on { case (t1, t2) =>
        t1.byIssue(t2.userName, t2.repositoryName, t2.issueId)
      }
      .innerJoin(IssueOutline).on { case ((t1, t2), t3) =>
        t2.byIssue(t3.userName, t3.repositoryName, t3.issueId)
      }
      .filter { case ((t1, t2), t3) =>
        keywords.map { query =>
          t1.content.toLowerCase like (s"%${likeEncode(query)}%", '^')
        }.reduceLeft(_ && _)
      }
      .map { case ((t1, t2), t3) =>
        (t2, t1.commentId, t1.content.?, t3.commentCount)
      }

    issues.union(comments).sortBy { case (issue, commentId, _, _) =>
      issue.issueId -> commentId
    }.list.splitWith { case ((issue1, _, _, _), (issue2, _, _, _)) =>
      issue1.issueId == issue2.issueId
    }.map { _.head match {
        case (issue, _, content, commentCount) => (issue, commentCount, content.getOrElse(""))
      }
    }.toList
  }

  def closeIssuesFromMessage(message: String, userName: String, owner: String, repository: String)(implicit s: Session) = {
    extractCloseId(message).foreach { issueId =>
      for(issue <- getIssue(owner, repository, issueId) if !issue.closed){
        createComment(owner, repository, userName, issue.issueId, "Close", "close")
        updateClosed(owner, repository, issue.issueId, true)
      }
    }
  }
}

object IssuesService {
  import javax.servlet.http.HttpServletRequest

  val IssueLimit = 30

  case class IssueSearchCondition(
      labels: Set[String] = Set.empty,
      milestone: Option[Option[String]] = None,
      author: Option[String] = None,
      assigned: Option[String] = None,
      mentioned: Option[String] = None,
      state: String = "open",
      sort: String = "created",
      direction: String = "desc",
      visibility: Option[String] = None,
      groups: Set[String] = Set.empty){

    def isEmpty: Boolean = {
      labels.isEmpty && milestone.isEmpty && author.isEmpty && assigned.isEmpty &&
        state == "open" && sort == "created" && direction == "desc" && visibility.isEmpty
    }

    def nonEmpty: Boolean = !isEmpty

    def toFilterString: String = (
      List(
        Some(s"is:${state}"),
        author.map(author => s"author:${author}"),
        assigned.map(assignee => s"assignee:${assignee}"),
        mentioned.map(mentioned => s"mentions:${mentioned}")
      ).flatten ++
      labels.map(label => s"label:${label}") ++
      List(
        milestone.map { _ match {
          case Some(x) => s"milestone:${x}"
          case None    => "no:milestone"
        }},
        (sort, direction) match {
          case ("created" , "desc") => None
          case ("created" , "asc" ) => Some("sort:created-asc")
          case ("comments", "desc") => Some("sort:comments-desc")
          case ("comments", "asc" ) => Some("sort:comments-asc")
          case ("updated" , "desc") => Some("sort:updated-desc")
          case ("updated" , "asc" ) => Some("sort:updated-asc")
        },
        visibility.map(visibility => s"visibility:${visibility}")
      ).flatten ++
      groups.map(group => s"group:${group}")
    ).mkString(" ")

    def toURL: String =
      "?" + List(
        if(labels.isEmpty) None else Some("labels=" + urlEncode(labels.mkString(","))),
        milestone.map { _ match {
          case Some(x) => "milestone=" + urlEncode(x)
          case None    => "milestone=none"
        }},
        author   .map(x => "author="    + urlEncode(x)),
        assigned .map(x => "assigned="  + urlEncode(x)),
        mentioned.map(x => "mentioned=" + urlEncode(x)),
        Some("state="     + urlEncode(state)),
        Some("sort="      + urlEncode(sort)),
        Some("direction=" + urlEncode(direction)),
        visibility.map(x => "visibility=" + urlEncode(x)),
        if(groups.isEmpty) None else Some("groups=" + urlEncode(groups.mkString(",")))
      ).flatten.mkString("&")

  }

  object IssueSearchCondition {

    private def param(request: HttpServletRequest, name: String, allow: Seq[String] = Nil): Option[String] = {
      val value = request.getParameter(name)
      if(value == null || value.isEmpty || (allow.nonEmpty && !allow.contains(value))) None else Some(value)
    }

    /**
     * Restores IssueSearchCondition instance from filter query.
     */
    def apply(filter: String, milestones: Map[String, Int]): IssueSearchCondition = {
      val conditions = filter.split("[ 　\t]+").map { x =>
        val dim = x.split(":")
        dim(0) -> dim(1)
      }.groupBy(_._1).map { case (key, values) =>
        key -> values.map(_._2).toSeq
      }

      val (sort, direction) = conditions.get("sort").flatMap(_.headOption).getOrElse("created-desc") match {
        case "created-asc"   => ("created" , "asc" )
        case "comments-desc" => ("comments", "desc")
        case "comments-asc"  => ("comments", "asc" )
        case "updated-desc"  => ("comments", "desc")
        case "updated-asc"   => ("comments", "asc" )
        case _               => ("created" , "desc")
      }

      IssueSearchCondition(
        conditions.get("label").map(_.toSet).getOrElse(Set.empty),
        conditions.get("milestone").flatMap(_.headOption) match {
          case None         => None
          case Some("none") => Some(None)
          case Some(x)      => Some(Some(x)) //milestones.get(x).map(x => Some(x))
        },
        conditions.get("author").flatMap(_.headOption),
        conditions.get("assignee").flatMap(_.headOption),
        conditions.get("mentions").flatMap(_.headOption),
        conditions.get("is").getOrElse(Seq.empty).find(x => x == "open" || x == "closed").getOrElse("open"),
        sort,
        direction,
        conditions.get("visibility").flatMap(_.headOption),
        conditions.get("group").map(_.toSet).getOrElse(Set.empty)
      )
    }

    /**
     * Restores IssueSearchCondition instance from request parameters.
     */
    def apply(request: HttpServletRequest): IssueSearchCondition =
      IssueSearchCondition(
        param(request, "labels").map(_.split(",").toSet).getOrElse(Set.empty),
        param(request, "milestone").map {
          case "none" => None
          case x      => Some(x)
        },
        param(request, "author"),
        param(request, "assigned"),
        param(request, "mentioned"),
        param(request, "state",     Seq("open", "closed")).getOrElse("open"),
        param(request, "sort",      Seq("created", "comments", "updated")).getOrElse("created"),
        param(request, "direction", Seq("asc", "desc")).getOrElse("desc"),
        param(request, "visibility"),
        param(request, "groups").map(_.split(",").toSet).getOrElse(Set.empty)
      )

    def page(request: HttpServletRequest) = try {
      val i = param(request, "page").getOrElse("1").toInt
      if(i <= 0) 1 else i
    } catch {
      case e: NumberFormatException => 1
    }
  }

  case class IssueInfo(issue: Issue, labels: List[Label], milestone: Option[String], commentCount: Int)

}
