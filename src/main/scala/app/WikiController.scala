package app

import util.{WikiUtil, JGitUtil}
import jp.sf.amateras.scalatra.forms._

class WikiController extends ControllerBase {

  case class WikiPageEditForm(pageName: String, content: String, message: Option[String], currentPageName: String)
  
  val form = mapping(
    "pageName"        -> trim(label("Page name"          , text(required, maxlength(40)))), 
    "content"         -> trim(label("Content"            , text(required))),
    "message"         -> trim(label("Message"            , optional(text()))),
    "currentPageName" -> trim(label("Current page name"  , text()))
  )(WikiPageEditForm.apply)
  
  get("/:owner/:repository/wiki"){
    val owner      = params("owner")
    val repository = params("repository")
    
    html.wiki("Home", 
        WikiUtil.getPage(owner, repository, "Home"), 
        JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }
  
  get("/:owner/:repository/wiki/:page"){
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")
    
    html.wiki(page, 
        WikiUtil.getPage(owner, repository, page), 
        JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }
  
  get("/:owner/:repository/wiki/:page/_edit"){
    val owner      = params("owner")
    val repository = params("repository")
    val page       = params("page")
    
    html.wikiedit(page, 
        WikiUtil.getPage(owner, repository, page), 
        JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }
  
  post("/:owner/:repository/wiki/_save", form){ form =>
    val owner      = params("owner")
    val repository = params("repository")
    
    WikiUtil.savePage(owner, repository, form.currentPageName, form.pageName, 
        form.content, LoginUser, form.message.getOrElse(""))
    
    redirect("%s/%s/wiki/%s".format(owner, repository, form.pageName))
  }
  
  get("/:owner/:repository/wiki/_new"){
    val owner      = params("owner")
    val repository = params("repository")
    
    html.wikiedit("", None, 
        JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }
}