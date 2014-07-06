package app

import util._
import ControlUtil._
import Implicits._
import service._
import jp.sf.amateras.scalatra.forms._

class SearchController extends SearchControllerBase
  with RepositoryService with AccountService with ActivityService with RepositorySearchService with IssuesService with ReferrerAuthenticator

trait SearchControllerBase extends ControllerBase { self: RepositoryService
  with ActivityService with RepositorySearchService with ReferrerAuthenticator =>

  val searchForm = mapping(
    "query"      -> trim(text(required)),
    "owner"      -> trim(text(required)),
    "repository" -> trim(text(required))
  )(SearchForm.apply)

  case class SearchForm(query: String, owner: String, repository: String)

  post("/search", searchForm){ form =>
    redirect(s"/${form.owner}/${form.repository}/search?q=${StringUtil.urlEncode(form.query)}")
  }

  get("/:owner/:repository/search")(referrersOnly { repository =>
    defining(params("q").trim, params.getOrElse("type", "code")){ case (query, target) =>
      val page   = try {
        val i = params.getOrElse("page", "1").toInt
        if(i <= 0) 1 else i
      } catch {
        case e: NumberFormatException => 1
      }

      target.toLowerCase match {
        case "issue" => search.html.issues(
          searchIssues(repository.owner, repository.name, query),
          countFiles(repository.owner, repository.name, query),
          query, page, repository)

        case _ => search.html.code(
          searchFiles(repository.owner, repository.name, query),
          countIssues(repository.owner, repository.name, query),
          query, page, repository)
      }
    }
  })

}
