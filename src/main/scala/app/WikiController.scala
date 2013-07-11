package app

import service._
import util.{CollaboratorsAuthenticator, ReferrerAuthenticator, JGitUtil}
import util.Directory._
import jp.sf.amateras.scalatra.forms._

class WikiController extends WikiControllerBase 
  with WikiService with RepositoryService with AccountService with ActivityService
  with CollaboratorsAuthenticator with ReferrerAuthenticator

trait WikiControllerBase extends ControllerBase {
  self: WikiService with RepositoryService with ActivityService
    with CollaboratorsAuthenticator with ReferrerAuthenticator =>

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
  
  get("/:owner/:repository/wiki")(referrersOnly { repository =>
    getWikiPage(repository.owner, repository.name, "Home").map { page =>
      wiki.html.page("Home", page, repository, hasWritePermission(repository.owner, repository.name, context.loginAccount))
    } getOrElse redirect(s"/${repository.owner}/${repository.name}/wiki/Home/_edit")
  })
  
  get("/:owner/:repository/wiki/:page")(referrersOnly { repository =>
    val pageName = params("page")

    getWikiPage(repository.owner, repository.name, pageName).map { page =>
      wiki.html.page(pageName, page, repository, hasWritePermission(repository.owner, repository.name, context.loginAccount))
    } getOrElse redirect(s"/${repository.owner}/${repository.name}/wiki/${pageName}/_edit") // TODO URLEncode
  })
  
  get("/:owner/:repository/wiki/:page/_history")(referrersOnly { repository =>
    val pageName = params("page")

    JGitUtil.withGit(getWikiRepositoryDir(repository.owner, repository.name)){ git =>
      wiki.html.history(Some(pageName), JGitUtil.getCommitLog(git, "master", path = pageName + ".md")._1, repository)
    }
  })
  
  get("/:owner/:repository/wiki/:page/_compare/:commitId")(referrersOnly { repository =>
    val pageName = params("page")
    val commitId = params("commitId").split("\\.\\.\\.")

    JGitUtil.withGit(getWikiRepositoryDir(repository.owner, repository.name)){ git =>
      wiki.html.compare(Some(pageName), getWikiDiffs(git, commitId(0), commitId(1)), repository)
    }
  })
  
  get("/:owner/:repository/wiki/_compare/:commitId")(referrersOnly { repository =>
    val commitId   = params("commitId").split("\\.\\.\\.")

    JGitUtil.withGit(getWikiRepositoryDir(repository.owner, repository.name)){ git =>
      wiki.html.compare(None, getWikiDiffs(git, commitId(0), commitId(1)), repository)
    }
  })
  
  get("/:owner/:repository/wiki/:page/_edit")(collaboratorsOnly { repository =>
    val pageName = params("page")
    wiki.html.edit(pageName, getWikiPage(repository.owner, repository.name, pageName), repository)
  })
  
  post("/:owner/:repository/wiki/_edit", editForm)(collaboratorsOnly { (form, repository) =>
    val loginAccount = context.loginAccount.get
    
    saveWikiPage(repository.owner, repository.name, form.currentPageName, form.pageName,
        form.content, loginAccount, form.message.getOrElse(""))
    
    updateLastActivityDate(repository.owner, repository.name)
    recordEditWikiPageActivity(repository.owner, repository.name, loginAccount.userName, form.pageName)

    redirect(s"/${repository.owner}/${repository.name}/wiki/${form.pageName}")
  })
  
  get("/:owner/:repository/wiki/_new")(collaboratorsOnly {
    wiki.html.edit("", None, _)
  })
  
  post("/:owner/:repository/wiki/_new", newForm)(collaboratorsOnly { (form, repository) =>
    val loginAccount = context.loginAccount.get
    
    saveWikiPage(repository.owner, repository.name, form.currentPageName, form.pageName,
        form.content, context.loginAccount.get, form.message.getOrElse(""))
    
    updateLastActivityDate(repository.owner, repository.name)
    recordCreateWikiPageActivity(repository.owner, repository.name, loginAccount.userName, form.pageName)

    redirect(s"/${repository.owner}/${repository.name}/wiki/${form.pageName}")
  })
  
  get("/:owner/:repository/wiki/:page/_delete")(collaboratorsOnly { repository =>
    val pageName = params("page")
    
    deleteWikiPage(repository.owner, repository.name, pageName, context.loginAccount.get.userName, s"Delete ${pageName}")
    updateLastActivityDate(repository.owner, repository.name)

    redirect(s"/${repository.owner}/${repository.name}/wiki")
  })
  
  get("/:owner/:repository/wiki/_pages")(referrersOnly { repository =>
    wiki.html.pages(getWikiPageList(repository.owner, repository.name), repository,
      hasWritePermission(repository.owner, repository.name, context.loginAccount))
  })
  
  get("/:owner/:repository/wiki/_history")(referrersOnly { repository =>
    JGitUtil.withGit(getWikiRepositoryDir(repository.owner, repository.name)){ git =>
      wiki.html.history(None, JGitUtil.getCommitLog(git, "master")._1, repository)
    }
  })

  get("/:owner/:repository/wiki/_blob/*")(referrersOnly { repository =>
    getFileContent(repository.owner, repository.name, multiParams("splat").head).map { content =>
        contentType = "application/octet-stream"
        content
    } getOrElse NotFound
  })

  private def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getWikiPageList(params("owner"), params("repository")).find(_ == value).map(_ => "Page already exists.")
  }

}