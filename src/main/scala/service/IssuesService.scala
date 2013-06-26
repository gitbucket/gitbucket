package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import scala.slick.jdbc.{StaticQuery => Q}
import Q.interpolation

import model._
import Issues._
import util.Implicits._

trait IssuesService {
  import IssuesService._

  def getIssue(owner: String, repository: String, issueId: String) =
    if (issueId forall (_.isDigit))
      Query(Issues) filter { t =>
        (t.userName is owner.bind) &&
        (t.repositoryName is repository.bind) &&
        (t.issueId is issueId.toInt.bind)
      } firstOption
    else None

  def getComment(owner: String, repository: String, issueId: Int) =
    Query(IssueComments) filter { t =>
      (t.userName is owner.bind) &&
      (t.repositoryName is repository.bind) &&
      (t.issueId is issueId.bind)
    } list

  /**
   * Returns the count of the search result against  issues.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param condition the search condition
   * @param filter the filter type ("all", "assigned" or "created_by")
   * @param userName the filter user name required for "assigned" and "created_by"
   * @return the count of the search result
   */
  def countIssue(owner: String, repository: String, condition: IssueSearchCondition, filter: String, userName: Option[String]): Int = {
    // TODO It must be _.length instead of map (_.issueId) list).length. But it does not work on Slick 1.0.1 (worked on Slick 1.0.0).
    (searchIssueQuery(owner, repository, condition, filter, userName) map (_.issueId) list).length
  }
  /**
   * Returns the Map which contains issue count for each labels.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param condition the search condition
   * @param filter the filter type ("all", "assigned" or "created_by")
   * @param userName the filter user name required for "assigned" and "created_by"
   * @return the Map which contains issue count for each labels (key is label name, value is issue count),
   */
  def countIssueGroupByLabels(owner: String, repository: String, condition: IssueSearchCondition,
                              filter: String, userName: Option[String]): Map[String, Int] = {

    Issues
      .innerJoin(IssueLabels).on { (t1, t2) =>
        (t1.userName is t2.userName) && (t1.repositoryName is t2.repositoryName) && (t1.issueId is t2.issueId)
      }
      .innerJoin(Labels).on { case ((t1, t2), t3) =>
        (t2.userName is t3.userName) && (t2.repositoryName is t3.repositoryName) && (t2.labelId is t3.labelId)
      }
      .filter { case ((t1, t2), t3) =>
        (t1.userName         is owner.bind) &&
        (t1.repositoryName   is repository.bind) &&
        (t1.closed           is (condition.state == "closed").bind) &&
        (t1.milestoneId      is condition.milestoneId.get.bind, condition.milestoneId.isDefined) &&
        (t1.assignedUserName is userName.get.bind, filter == "assigned") &&
        (t1.openedUserName   is userName.get.bind, filter == "created_by")
      }
      .groupBy { case ((t1, t2), t3) =>
        t3.labelName
      }
      .map { case (labelName, t) =>
        labelName ~ t.length
      }
      .list
      .toMap
  }

  /**
   * Returns the search result against  issues.
   *
   * @param owner the repository owner
   * @param repository the repository name
   * @param condition the search condition
   * @param filter the filter type ("all", "assigned" or "created_by")
   * @param userName the filter user name required for "assigned" and "created_by"
   * @return the count of the search result
   */
  def searchIssue(owner: String, repository: String, condition: IssueSearchCondition, filter: String, userName: Option[String]) =
    searchIssueQuery(owner, repository, condition, filter, userName).sortBy { t =>
      (condition.sort match {
        case "created"  => t.registeredDate
        case "comments" => t.updatedDate
        case "updated"  => t.updatedDate
      }) match {
        case sort => condition.direction match {
          case "asc"  => sort asc
          case "desc" => sort desc
        }
      }
    } list

  /**
   * Assembles query for conditional issue searching.
   */
  private def searchIssueQuery(owner: String, repository: String, condition: IssueSearchCondition, filter: String, userName: Option[String]) =
    Query(Issues) filter { t =>
      (t.userName         is owner.bind) &&
        (t.repositoryName   is repository.bind) &&
        (t.closed           is (condition.state == "closed").bind) &&
        (t.milestoneId      is condition.milestoneId.get.bind, condition.milestoneId.isDefined) &&
        //if(condition.labels.nonEmpty) Some(Query(Issue)) else None,
        (t.assignedUserName is userName.get.bind, filter == "assigned") &&
        (t.openedUserName   is userName.get.bind, filter == "created_by")
    }

  def saveIssue(owner: String, repository: String, loginUser: String,
      title: String, content: Option[String]) =
    // next id number
    sql"SELECT ISSUE_ID + 1 FROM ISSUE_ID WHERE USER_NAME = $owner AND REPOSITORY_NAME = $repository FOR UPDATE".as[Int]
        .firstOption.filter { id =>
      Issues insert Issue(
          owner,
          repository,
          id,
          loginUser,
          None,
          None,
          title,
          content,
          false,
          currentDate,
          currentDate)

      // increment issue id
      IssueId.filter { t =>
        (t.userName is owner.bind) && (t.repositoryName is repository.bind)
      }.map(_.issueId).update(id) > 0
    } get

  def saveComment(owner: String, repository: String, loginUser: String,
      issueId: Int, content: String) =
    IssueComments.autoInc insert (
        owner,
        repository,
        issueId,
        loginUser,
        content,
        currentDate,
        currentDate)

}

object IssuesService {
  import java.net.URLEncoder
  import javax.servlet.http.HttpServletRequest

  case class IssueSearchCondition(
      labels: Set[String]      = Set.empty,
      milestoneId: Option[Int] = None,
      state: String            = "open",
      sort: String             = "created",
      direction: String        = "desc"){

    import IssueSearchCondition._

    def toURL: String =
      "?" + List(
        if(labels.isEmpty) None else Some("labels=" + urlEncode(labels.mkString(" "))),
        milestoneId.map("milestone=" + _),
        Some("state="     + urlEncode(state)),
        Some("sort="      + urlEncode(sort)),
        Some("direction=" + urlEncode(direction))).flatten.mkString("&")

  }

  object IssueSearchCondition {

    private def urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private def param(request: HttpServletRequest, name: String, allow: Seq[String] = Nil): Option[String] = {
      val value = request.getParameter(name)
      if(value == null || value.isEmpty || (allow.nonEmpty && !allow.contains(value))) None else Some(value)
    }

    def apply(request: HttpServletRequest): IssueSearchCondition =
      IssueSearchCondition(
        param(request, "labels").map(_.split(" ").toSet).getOrElse(Set.empty),
        param(request, "milestone").map(_.toInt),
        param(request, "state",     Seq("open", "closed")).getOrElse("open"),
        param(request, "sort",      Seq("created", "comments", "updated")).getOrElse("created"),
        param(request, "direction", Seq("asc", "desc")).getOrElse("desc"))
  }
}
