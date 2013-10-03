package app

import service._
import util.{CollaboratorsAuthenticator, ReferrerAuthenticator, JGitUtil, StringUtil}
import util.Directory._
import util.ControlUtil._
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.PatchApplyException
import org.scalatra.FlashMapSupport

class WikiController extends WikiControllerBase 
  with WikiService with RepositoryService with AccountService with ActivityService
  with CollaboratorsAuthenticator with ReferrerAuthenticator

trait WikiControllerBase extends ControllerBase with FlashMapSupport {
  self: WikiService with RepositoryService with ActivityService
    with CollaboratorsAuthenticator with ReferrerAuthenticator =>

  case class WikiPageEditForm(pageName: String, content: String, message: Option[String], currentPageName: String)
  
  val newForm = mapping(
    "pageName"        -> trim(label("Page name"          , text(required, maxlength(40), pagename, unique))),
    "content"         -> trim(label("Content"            , text(required))),
    "message"         -> trim(label("Message"            , optional(text()))),
    "currentPageName" -> trim(label("Current page name"  , text()))
  )(WikiPageEditForm.apply)
  
  val editForm = mapping(
    "pageName"        -> trim(label("Page name"          , text(required, maxlength(40), pagename))),
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
    val pageName = StringUtil.urlDecode(params("page"))

    getWikiPage(repository.owner, repository.name, pageName).map { page =>
      wiki.html.page(pageName, page, repository, hasWritePermission(repository.owner, repository.name, context.loginAccount))
    } getOrElse redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}/_edit")
  })
  
  get("/:owner/:repository/wiki/:page/_history")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
      JGitUtil.getCommitLog(git, "master", path = pageName + ".md") match {
        case Right((logs, hasNext)) => wiki.html.history(Some(pageName), logs, repository)
        case Left(_) => NotFound
      }
    }
  })
  
  get("/:owner/:repository/wiki/:page/_compare/:commitId")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    defining(params("commitId").split("\\.\\.\\.")){ case Array(from, to) =>
      using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
        wiki.html.compare(Some(pageName), from, to, JGitUtil.getDiffs(git, from, to, true), repository,
          hasWritePermission(repository.owner, repository.name, context.loginAccount), flash.get("info"))
      }
    }
  })
  
  get("/:owner/:repository/wiki/_compare/:commitId")(referrersOnly { repository =>
    defining(params("commitId").split("\\.\\.\\.")){ case Array(from, to) =>
      using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
        wiki.html.compare(None, from, to, JGitUtil.getDiffs(git, from, to, true), repository,
          hasWritePermission(repository.owner, repository.name, context.loginAccount), flash.get("info"))
      }
    }
  })

  get("/:owner/:repository/wiki/:page/_revert/:commitId")(collaboratorsOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    defining(params("commitId").split("\\.\\.\\.")){ case Array(from, to) =>
      if(revertWikiPage(repository.owner, repository.name, from, to, context.loginAccount.get, Some(pageName))){
        redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}")
      } else {
        flash += "info" -> "This patch was not able to be reversed."
        redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}/_compare/${from}...${to}")
      }
    }
  })

  get("/:owner/:repository/wiki/_revert/:commitId")(collaboratorsOnly { repository =>
    defining(params("commitId").split("\\.\\.\\.")){ case Array(from, to) =>
      if(revertWikiPage(repository.owner, repository.name, from, to, context.loginAccount.get, None)){
        redirect(s"/${repository.owner}/${repository.name}/wiki/}")
      } else {
        flash += "info" -> "This patch was not able to be reversed."
        redirect(s"/${repository.owner}/${repository.name}/wiki/_compare/${from}...${to}")
      }
    }
  })

  get("/:owner/:repository/wiki/:page/_edit")(collaboratorsOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))
    wiki.html.edit(pageName, getWikiPage(repository.owner, repository.name, pageName), repository)
  })
  
  post("/:owner/:repository/wiki/_edit", editForm)(collaboratorsOnly { (form, repository) =>
    defining(context.loginAccount.get){ loginAccount =>
      saveWikiPage(repository.owner, repository.name, form.currentPageName, form.pageName,
        form.content, loginAccount, form.message.getOrElse("")).map { commitId =>
        updateLastActivityDate(repository.owner, repository.name)
        recordEditWikiPageActivity(repository.owner, repository.name, loginAccount.userName, form.pageName, commitId)
      }
      redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(form.pageName)}")
    }
  })
  
  get("/:owner/:repository/wiki/_new")(collaboratorsOnly {
    wiki.html.edit("", None, _)
  })
  
  post("/:owner/:repository/wiki/_new", newForm)(collaboratorsOnly { (form, repository) =>
    defining(context.loginAccount.get){ loginAccount =>
      saveWikiPage(repository.owner, repository.name, form.currentPageName, form.pageName,
          form.content, loginAccount, form.message.getOrElse(""))

      updateLastActivityDate(repository.owner, repository.name)
      recordCreateWikiPageActivity(repository.owner, repository.name, loginAccount.userName, form.pageName)

      redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(form.pageName)}")
    }
  })
  
  get("/:owner/:repository/wiki/:page/_delete")(collaboratorsOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    defining(context.loginAccount.get){ loginAccount =>
      deleteWikiPage(repository.owner, repository.name, pageName, loginAccount.userName, loginAccount.mailAddress, s"Destroyed ${pageName}")
      updateLastActivityDate(repository.owner, repository.name)

      redirect(s"/${repository.owner}/${repository.name}/wiki")
    }
  })
  
  get("/:owner/:repository/wiki/_pages")(referrersOnly { repository =>
    wiki.html.pages(getWikiPageList(repository.owner, repository.name), repository,
      hasWritePermission(repository.owner, repository.name, context.loginAccount))
  })
  
  get("/:owner/:repository/wiki/_history")(referrersOnly { repository =>
    using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
      JGitUtil.getCommitLog(git, "master") match {
        case Right((logs, hasNext)) => wiki.html.history(None, logs, repository)
        case Left(_) => NotFound
      }
    }
  })

  get("/:owner/:repository/wiki/_blob/*")(referrersOnly { repository =>
    getFileContent(repository.owner, repository.name, multiParams("splat").head).map { content =>
      contentType = "application/octet-stream"
      content
    } getOrElse NotFound
  })

  private def unique: Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String]): Option[String] =
      getWikiPageList(params("owner"), params("repository")).find(_ == value).map(_ => "Page already exists.")
  }

  private def pagename: Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] =
      if(value.exists("\\/:*?\"<>|".contains(_))){
        Some(s"${name} contains invalid character.")
      } else if(value.startsWith("_") || value.startsWith("-")){
        Some(s"${name} starts with invalid character.")
      } else {
        None
      }
  }


}