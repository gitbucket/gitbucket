package app

import util._
import util.Directory._
import service._
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.revwalk.RevWalk
import scala.collection.mutable.ListBuffer
import org.eclipse.jgit.lib.FileMode
import java.util.regex.Pattern

class IndexController extends IndexControllerBase 
  with RepositoryService with AccountService with SystemSettingsService with ActivityService
  with ReferrerAuthenticator

trait IndexControllerBase extends ControllerBase { self: RepositoryService 
  with SystemSettingsService with ActivityService
  with ReferrerAuthenticator =>

  val searchForm = mapping(
    "query"      -> trim(text(required)), // TODO optional?
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

  // TODO readable only
  get("/:owner/:repository/search")(referrersOnly { repository =>
    val owner  = params("owner")
    val name   = params("repository")
    val query  = params("q")
    val target = params.getOrElse("type", "Code")

    target.toLowerCase match {
      case "issue" => {
        // TODO search issue
      }
      case _ => {
        JGitUtil.withGit(getRepositoryDir(owner, name)){ git =>
          val revWalk = new RevWalk(git.getRepository)
          val objectId = git.getRepository.resolve("HEAD")
          val revCommit = revWalk.parseCommit(objectId)
          val treeWalk = new TreeWalk(git.getRepository)
          treeWalk.setRecursive(true)
          treeWalk.addTree(revCommit.getTree)

          val lowerQueries = query.toLowerCase.split("[ \\t　]+")
          val list = new ListBuffer[(String, String)]
          while (treeWalk.next()) {
            if(treeWalk.getFileMode(0) != FileMode.TREE){
              JGitUtil.getContent(git, treeWalk.getObjectId(0), false).foreach { bytes =>
                if(FileUtil.isText(bytes)){
                  val text = new String(bytes, "UTF-8")
                  val lowerText = text.toLowerCase
                  val indices = lowerQueries.map { lowerQuery =>
                    lowerText.indexOf(lowerQuery)
                  }
                  if(!indices.exists(_ < 0)){
                    val lineNumber = text.substring(0, indices.min).split("\n").size - 1
                    val highlightText = StringUtil.escapeHtml(text.split("\n").drop(lineNumber).take(5).mkString("\n"))
                      .replaceAll("(?i)(" + lowerQueries.map("\\Q" + _ + "\\E").mkString("|") +  ")",
                                  "<span style=\"background-color: yellow;\">$1</span>")
                    list.append((treeWalk.getPathString, highlightText))
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

  private def searchDirectory(query: String, dir: java.io.File, matched: List[java.io.File] = Nil): List[java.io.File] = {
    dir.listFiles.toList.flatMap {
      case file if(file.isDirectory && file.getName != ".git") => searchDirectory(query, file, matched)
      case file if(file.isFile && FileUtils.readFileToString(file).contains(query)) => matched :+ file
      case _ => matched
    }
  }

}

case class FileSearchResult(path: String, lastModified: java.util.Date, highlightText: String)