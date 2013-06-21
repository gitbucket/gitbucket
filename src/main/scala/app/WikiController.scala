package app

import service._
import util.{WritableRepositoryAuthenticator, ReadableRepositoryAuthenticator, JGitUtil}
import util.Directory._
import jp.sf.amateras.scalatra.forms._

class WikiController extends WikiControllerBase 
  with WikiService with RepositoryService with AccountService with WritableRepositoryAuthenticator with ReadableRepositoryAuthenticator

trait WikiControllerBase extends ControllerBase {
  self: WikiService with RepositoryService with WritableRepositoryAuthenticator with ReadableRepositoryAuthenticator =>

  case class WikiPageEditForm(pageName: String, content: String, message: Option[String], currentPageName: String)
  
  val newForm = mapping(
    "pageName"        -> trim(label("Page name"          , text(required, maxlength(40), identifier, unique))),
    "content"         -> trim(label("Content"            , text(required))),
    "message"         -> trim(label("Message"            , optional(text()))),
    "currentPageName" -> trim(label("Current page name"  , text()))
  )(WikiPageEditForm.apply)
  
  val editForm = mapping(
    "pageName"        -> trim(label("Page name"          , text(required, maxlength(40), identifier))),
    "content"         -> trim(label("Content"            , text(required))),
    "message"         -> trim(label("Message"            , optional(text()))),
    "currentPageName" -> trim(label("Current page name"  , text(required)))
  )(WikiPageEditForm.apply)
  
  get("/:owner/:repository/wiki")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case Some(repoInfo) => getWikiPage(owner, repository, "Home") match {
        case Some(page) => wiki.html.wiki("Home", page, repoInfo, isWritable(owner, repository))
        case None => redirect("/%s/%s/wiki/Home/_edit".format(owner, repository))
      }
      case None => NotFound()
    }
  })
  
  get("/:owner/:repository/wiki/:page")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val pageName   = params("page")
    
    getWikiPage(owner, repository, pageName) match {
      case Some(page) => wiki.html.wiki(pageName, page, getRepository(owner, repository, baseUrl).get, isWritable(owner, repository))
      case None => redirect("/%s/%s/wiki/%s/_edit".format(owner, repository, pageName)) // TODO URLEncode
    }
  })
  
  get("/:owner/:repository/wiki/:page/_history")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")

    getRepository(owner, repository, baseUrl) match {
      case Some(repoInfo) => JGitUtil.withGit(getWikiRepositoryDir(owner, repository)){ git =>
        wiki.html.wikihistory(Some(page), JGitUtil.getCommitLog(git, "master", path = page + ".md")._1, repoInfo)
      }
      case None => NotFound()
    }
  })
  
  get("/:owner/:repository/wiki/:page/_compare/:commitId")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")
    val commitId   = params("commitId").split("\\.\\.\\.")
    
    JGitUtil.withGit(getWikiRepositoryDir(owner, repository)){ git =>
      wiki.html.wikicompare(Some(page),
        getWikiDiffs(git, commitId(0), commitId(1)), getRepository(owner, repository, baseUrl).get)
    }
  })
  
  get("/:owner/:repository/wiki/_compare/:commitId")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val commitId   = params("commitId").split("\\.\\.\\.")

    getRepository(owner, repository, baseUrl) match {
      case Some(repoInfo) => JGitUtil.withGit(getWikiRepositoryDir(owner, repository)){ git =>
        wiki.html.wikicompare(None, getWikiDiffs(git, commitId(0), commitId(1)), repoInfo)
      }
      case None => NotFound()
    }
  })
  
  get("/:owner/:repository/wiki/:page/_edit")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")

    getRepository(owner, repository, baseUrl) match {
      case Some(repoInfo) => wiki.html.wikiedit(page, getWikiPage(owner, repository, page), repoInfo)
      case None => NotFound()
    }
  })
  
  post("/:owner/:repository/wiki/_edit", editForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")
    
    saveWikiPage(owner, repository, form.currentPageName, form.pageName, 
        form.content, context.loginAccount.get, form.message.getOrElse(""))
    
    redirect("%s/%s/wiki/%s".format(owner, repository, form.pageName))
  })
  
  get("/:owner/:repository/wiki/_new")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case Some(repoInfo) => wiki.html.wikiedit("", None, repoInfo)
      case None => NotFound()
    }
  })
  
  post("/:owner/:repository/wiki/_new", newForm)(writableRepository { form =>
    val owner      = params("owner")
    val repository = params("repository")
    
    saveWikiPage(owner, repository, form.currentPageName, form.pageName, 
        form.content, context.loginAccount.get, form.message.getOrElse(""))
    
    redirect("%s/%s/wiki/%s".format(owner, repository, form.pageName))
  })
  
  get("/:owner/:repository/wiki/:page/_delete")(writableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")
    
    deleteWikiPage(owner, repository, page, context.loginAccount.get.userName, "Delete %s".format(page))
    
    redirect("%s/%s/wiki".format(owner, repository))
  })
  
  get("/:owner/:repository/wiki/_pages")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case Some(repoInfo) => wiki.html.wikipages(getWikiPageList(owner, repository), repoInfo, isWritable(owner, repository))
      case None => NotFound()
    }
  })
  
  get("/:owner/:repository/wiki/_history")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")

    getRepository(owner, repository, baseUrl) match {
      case Some(repoInfo) => JGitUtil.withGit(getWikiRepositoryDir(owner, repository)){ git =>
        wiki.html.wikihistory(None, JGitUtil.getCommitLog(git, "master")._1, repoInfo)
      }
      case None => NotFound()
    }
  })

  get("/:owner/:repository/wiki/_blob/*")(readableRepository {
    val owner      = params("owner")
    val repository = params("repository")
    val path       = multiParams("splat").head

    getFileContent(owner, repository, path) match {
      case Some(content) => {
        contentType = "application/octet-stream"
        content
      }
      case None => NotFound()
    }
  })

  private def isWritable(owner: String, repository: String): Boolean = {
    context.loginAccount match {
      case Some(a) if(a.isAdmin) => true
      case Some(a) if(a.userName == owner) => true
      case Some(a) if(getCollaborators(owner, repository).contains(a.userName)) => true
      case _ => false
    }
  }

  private def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getWikiPageList(params("owner"), params("repository")).find(_ == value).map(_ => "Page already exists.")
  }

}