package app

import util._
import util.Directory._
import service._
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.apache.commons.io.FileUtils

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
        // TODO search code
          val dir = new java.io.File(getTemporaryDir(owner, name), "search")
          if(!dir.exists){
            val git = Git
                .cloneRepository.setDirectory(dir)
                .setURI(getRepositoryDir(owner, name).toURI.toString)
                .setBranch(repository.repository.defaultBranch)
                .call
            git.getRepository.close
          } else {
            val git = Git.open(dir)
            git.pull.call
            if(git.getRepository.getBranch != repository.repository.defaultBranch){
              git.checkout.setName(repository.repository.defaultBranch).call
            }
            git.getRepository.close
          }

          search.html.code(searchDirectory(query, dir).map { file =>
            FileSearchResult(
              file.getAbsolutePath.substring(dir.getAbsolutePath.length + 1).replace('\\', '/'),
              new java.util.Date(file.lastModified)
           )
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

case class FileSearchResult(path: String, lastModified: java.util.Date)