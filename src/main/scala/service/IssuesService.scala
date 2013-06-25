package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import scala.slick.jdbc.{StaticQuery => Q}
import Q.interpolation

import model._
import Issues._

trait IssuesService {
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

  def searchIssue(owner: String, repository: String,
      // TODO It is better to have a DTO
      closed: Boolean) =
    Query(Issues) filter { t =>
      (t.userName is owner.bind) &&
      (t.repositoryName is repository.bind) &&
      (t.closed is closed.bind)
    } list

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

  case class IssueSearchCondition(labels: Set[String], milestoneId: Option[Int], state: String, sort: String, direction: String){
    import IssueSearchCondition._

    def toURL(repository: service.RepositoryService.RepositoryInfo)(implicit context: app.Context): String = {
      val params = List(
        if(labels.isEmpty) None else Some("labels=" + urlEncode(labels.mkString(" "))),
        milestoneId.map("milestone=" + _),
        Some("state="     + urlEncode(state)),
        Some("sort="      + urlEncode(sort)),
        Some("direction=" + urlEncode(direction))
      )
      "%s/%s/%s/issues?%s".format(context.path, repository.owner, repository.name, params.flatten.mkString("&"))
    }
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
