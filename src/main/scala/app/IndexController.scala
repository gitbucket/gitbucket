package app

import util._
import util.Directory._
import service._
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.revwalk.RevWalk
import scala.collection.mutable.ListBuffer
import org.eclipse.jgit.lib.FileMode

class IndexController extends IndexControllerBase 
  with RepositoryService with AccountService with SystemSettingsService with ActivityService with IssuesService
  with ReferrerAuthenticator

trait IndexControllerBase extends ControllerBase { self: RepositoryService 
  with SystemSettingsService with ActivityService with IssuesService
  with ReferrerAuthenticator =>

  val searchForm = mapping(
    "query"      -> trim(text(required)),
    "owner"      -> trim(text(required)),
    "repository" -> trim(text(required))
  )(SearchForm.apply)

  case class SearchForm(query: String, owner: String, repository: String)

  get("/"){
    val loginAccount = context.loginAccount

    html.index(getRecentActivities(),
      getAccessibleRepositories(loginAccount, baseUrl),
      loadSystemSettings(),
      loginAccount.map{ account => getRepositoryNamesOfUser(account.userName) }.getOrElse(Nil)
    )
  }

  post("/search", searchForm){ form =>
    redirect(s"${form.owner}/${form.repository}/search?q=${StringUtil.urlEncode(form.query)}")
  }

  get("/:owner/:repository/search")(referrersOnly { repository =>
    val query  = params("q")
    val target = params.getOrElse("type", "Code")

    target.toLowerCase match {
      case "issue" => {
        search.html.issues(queryIssues(repository.owner, repository.name, query).map { case (issue, commentCount, content) =>
          IssueSearchResult(
            issue.issueId,
            issue.title,
            issue.openedUserName,
            issue.registeredDate,
            commentCount,
            getHighlightText(content, query))
        }, query, repository)

      }
      case _ => {
        // TODO move to JGitUtil?
        JGitUtil.withGit(getRepositoryDir(repository.owner, repository.name)){ git =>
          val revWalk   = new RevWalk(git.getRepository)
          val objectId  = git.getRepository.resolve("HEAD")
          val revCommit = revWalk.parseCommit(objectId)
          val treeWalk  = new TreeWalk(git.getRepository)
          treeWalk.setRecursive(true)
          treeWalk.addTree(revCommit.getTree)

          val lowerQueries = StringUtil.splitWords(query.toLowerCase)
          val list = new ListBuffer[(String, String)]
          while (treeWalk.next()) {
            if(treeWalk.getFileMode(0) != FileMode.TREE){
              JGitUtil.getContent(git, treeWalk.getObjectId(0), false).foreach { bytes =>
                if(FileUtil.isText(bytes)){
                  val text      = new String(bytes, "UTF-8")
                  val lowerText = text.toLowerCase
                  val indices   = lowerQueries.map(lowerText.indexOf _)
                  if(!indices.exists(_ < 0)){
                    list.append((treeWalk.getPathString, getHighlightText(text, query)))
                  }
                }
              }
            }
          }
          treeWalk.release
          revWalk.release

          val commits = JGitUtil.getLatestCommitFromPaths(git, list.toList.map(_._1), "HEAD")

          search.html.code(list.toList.map { case (path, highlightText) =>
            FileSearchResult(path, commits(path).getCommitterIdent.getWhen, highlightText)
          }, query, repository)
        }
      }
    }
  })

  private def getHighlightText(content: String, query: String): String = {
    val lowerQueries = StringUtil.splitWords(query.toLowerCase)
    val lowerText    = content.toLowerCase
    val indices      = lowerQueries.map(lowerText.indexOf _)

    if(!indices.exists(_ < 0)){
      val lineNumber = content.substring(0, indices.min).split("\n").size - 1
      StringUtil.escapeHtml(content.split("\n").drop(lineNumber).take(5).mkString("\n"))
        .replaceAll("(?i)(" + lowerQueries.map("\\Q" + _ + "\\E").mkString("|") +  ")",
        "<span style=\"background-color: yellow;\">$1</span>")
    } else {
      content.split("\n").take(5).mkString("\n")
    }
  }

}


case class IssueSearchResult(issueId: Int, title: String, openedUserName: String, registeredDate: java.util.Date,
                             commentCount: Int, highlightText: String)

case class FileSearchResult(path: String, lastModified: java.util.Date, highlightText: String)