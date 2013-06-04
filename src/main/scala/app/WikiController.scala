package app

import service._
import util.JGitUtil
import util.Directory._
import jp.sf.amateras.scalatra.forms._

class WikiController extends WikiControllerBase 
  with WikiService with RepositoryService with AccountService

trait WikiControllerBase extends ControllerBase { self: WikiService with RepositoryService =>

  // TODO ユーザ名の先頭に_は使えないようにする
  case class WikiPageEditForm(pageName: String, content: String, message: Option[String], currentPageName: String)
  
  val newForm = mapping(
    "pageName"        -> trim(label("Page name"          , text(required, maxlength(40), pageName, unique))), 
    "content"         -> trim(label("Content"            , text(required))),
    "message"         -> trim(label("Message"            , optional(text()))),
    "currentPageName" -> trim(label("Current page name"  , text()))
  )(WikiPageEditForm.apply)
  
  val editForm = mapping(
    "pageName"        -> trim(label("Page name"          , text(required, maxlength(40), pageName))), 
    "content"         -> trim(label("Content"            , text(required))),
    "message"         -> trim(label("Message"            , optional(text()))),
    "currentPageName" -> trim(label("Current page name"  , text(required)))
  )(WikiPageEditForm.apply)
  
  get("/:owner/:repository/wiki"){
    val owner      = params("owner")
    val repository = params("repository")
    
    getWikiPage(owner, repository, "Home") match {
      case Some(page) => wiki.html.wiki("Home", page, getRepository(owner, repository, servletContext).get, isWritable(owner, repository))
      case None => redirect("/%s/%s/wiki/Home/_edit".format(owner, repository))
    }
  }
  
  get("/:owner/:repository/wiki/:page"){
    val owner      = params("owner")
    val repository = params("repository")
    val pageName   = params("page")
    
    getWikiPage(owner, repository, pageName) match {
      case Some(page) => wiki.html.wiki(pageName, page, getRepository(owner, repository, servletContext).get, isWritable(owner, repository))
      case None => redirect("/%s/%s/wiki/%s/_edit".format(owner, repository, pageName)) // TODO URLEncode
    }
  }
  
  get("/:owner/:repository/wiki/:page/_history"){
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")
    
    JGitUtil.withGit(getWikiRepositoryDir(owner, repository)){ git =>
      wiki.html.wikihistory(Some(page),
        JGitUtil.getCommitLog(git, "master", path = page + ".md")._1, getRepository(owner, repository, servletContext).get)
    }
  }
  
  get("/:owner/:repository/wiki/:page/_compare/:commitId"){
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")
    val commitId   = params("commitId").split("\\.\\.\\.")
    
    JGitUtil.withGit(getWikiRepositoryDir(owner, repository)){ git =>
      wiki.html.wikicompare(Some(page),
        getWikiDiffs(git, commitId(0), commitId(1)), getRepository(owner, repository, servletContext).get)
    }
  }
  
  get("/:owner/:repository/wiki/_compare/:commitId"){
    val owner      = params("owner")
    val repository = params("repository")
    val commitId   = params("commitId").split("\\.\\.\\.")
    
    JGitUtil.withGit(getWikiRepositoryDir(owner, repository)){ git =>
      wiki.html.wikicompare(None,
        getWikiDiffs(git, commitId(0), commitId(1)), getRepository(owner, repository, servletContext).get)
    }
  }
  
  get("/:owner/:repository/wiki/:page/_edit")(usersOnly {
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")
    
    wiki.html.wikiedit(page, 
        getWikiPage(owner, repository, page), getRepository(owner, repository, servletContext).get)
  })
  
  post("/:owner/:repository/wiki/_edit", editForm)(usersOnly { form =>
    val owner      = params("owner")
    val repository = params("repository")
    
    saveWikiPage(owner, repository, form.currentPageName, form.pageName, 
        form.content, context.loginAccount.get, form.message.getOrElse(""))
    
    redirect("%s/%s/wiki/%s".format(owner, repository, form.pageName))
  })
  
  get("/:owner/:repository/wiki/_new")(usersOnly {
    val owner      = params("owner")
    val repository = params("repository")
    
    wiki.html.wikiedit("", None, getRepository(owner, repository, servletContext).get)
  })
  
  post("/:owner/:repository/wiki/_new", newForm)(usersOnly { form =>
    val owner      = params("owner")
    val repository = params("repository")
    
    saveWikiPage(owner, repository, form.currentPageName, form.pageName, 
        form.content, context.loginAccount.get, form.message.getOrElse(""))
    
    redirect("%s/%s/wiki/%s".format(owner, repository, form.pageName))
  })
  
  get("/:owner/:repository/wiki/:page/_delete")(usersOnly {
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")
    
    deleteWikiPage(owner, repository, page, context.loginAccount.get.userName, "Delete %s".format(page))
    
    redirect("%s/%s/wiki".format(owner, repository))
  })
  
  get("/:owner/:repository/wiki/_pages"){
    val owner      = params("owner")
    val repository = params("repository")
    
    wiki.html.wikipages(getWikiPageList(owner, repository), getRepository(owner, repository, servletContext).get, isWritable(owner, repository))
  }
  
  get("/:owner/:repository/wiki/_history"){
    val owner      = params("owner")
    val repository = params("repository")
    
    JGitUtil.withGit(getWikiRepositoryDir(owner, repository)){ git =>
      wiki.html.wikihistory(None,
        JGitUtil.getCommitLog(git, "master")._1, getRepository(owner, repository, servletContext).get)
    }
  }
  
  post("/:owner/:repository/wiki/_preview"){
    val owner      = params("owner")
    val repository = params("repository")
    val content    = params("content")
    contentType = "text/html"
    view.helpers.markdown(content, getRepository(owner, repository, servletContext).get, true)
  }
  
  /**
   * Constraint for the wiki page name.
   */
  def pageName: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      if(!value.matches("^[a-zA-Z0-9\\-_]+$")){
        Some("Page name contains invalid character.")
      } else {
        None
      }
    }
  }
  
  def isWritable(owner: String, repository: String): Boolean = {
    context.loginAccount match {
      case Some(a) if(a.userType == AccountService.Administrator) => true
      case Some(a) if(a.userName == owner) => true
      case Some(a) if(getCollaborators(owner, repository).contains(a.userName)) => true
      case _ => false
    }
  }
  
  def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      if(getWikiPageList(params("owner"), params("repository")).contains(value)){
        Some("Page already exists.")
      } else {
        None
      }
    }
  }

}