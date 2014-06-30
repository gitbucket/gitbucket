package app

import service._
import util._
import util.Directory._
import util.ControlUtil._
import util.Implicits._
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.scalatra.i18n.Messages
import java.util.ResourceBundle

class WikiController extends WikiControllerBase 
  with WikiService with RepositoryService with AccountService with ActivityService with CollaboratorsAuthenticator with ReferrerAuthenticator

trait WikiControllerBase extends ControllerBase {
  self: WikiService with RepositoryService with ActivityService with CollaboratorsAuthenticator with ReferrerAuthenticator =>

  case class WikiPageEditForm(pageName: String, content: String, message: Option[String], currentPageName: String, id: String)
  
  val newForm = mapping(
    "pageName"        -> trim(label("Page name"         , text(required, maxlength(40), pagename, unique))),
    "content"         -> trim(label("Content"           , text(required, conflictForNew))),
    "message"         -> trim(label("Message"           , optional(text()))),
    "currentPageName" -> trim(label("Current page name" , text())),
    "id"              -> trim(label("Latest commit id"  , text()))
  )(WikiPageEditForm.apply)
  
  val editForm = mapping(
    "pageName"        -> trim(label("Page name"         , text(required, maxlength(40), pagename))),
    "content"         -> trim(label("Content"           , text(required, conflictForEdit))),
    "message"         -> trim(label("Message"           , optional(text()))),
    "currentPageName" -> trim(label("Current page name" , text(required))),
    "id"              -> trim(label("Latest commit id"  , text(required)))
  )(WikiPageEditForm.apply)
  
  get("/:owner/:repository/wiki")(referrersOnly { repository =>
    getWikiPage(repository.owner, repository.name, "Home").map { page =>
      wiki.html.page("Home", page, getWikiPageList(repository.owner, repository.name),
        repository, hasWritePermission(repository.owner, repository.name, context.loginAccount))
    } getOrElse redirect(s"/${repository.owner}/${repository.name}/wiki/Home/_edit")
  })
  
  get("/:owner/:repository/wiki/:page")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    getWikiPage(repository.owner, repository.name, pageName).map { page =>
      wiki.html.page(pageName, page, getWikiPageList(repository.owner, repository.name),
        repository, hasWritePermission(repository.owner, repository.name, context.loginAccount))
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
    val Array(from, to) = params("commitId").split("\\.\\.\\.")

    using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
      wiki.html.compare(Some(pageName), from, to, JGitUtil.getDiffs(git, from, to, true).filter(_.newPath == pageName + ".md"), repository,
        hasWritePermission(repository.owner, repository.name, context.loginAccount), flash.get("info"))
    }
  })
  
  get("/:owner/:repository/wiki/_compare/:commitId")(referrersOnly { repository =>
    val Array(from, to) = params("commitId").split("\\.\\.\\.")

    using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
      wiki.html.compare(None, from, to, JGitUtil.getDiffs(git, from, to, true), repository,
        hasWritePermission(repository.owner, repository.name, context.loginAccount), flash.get("info"))
    }
  })

  get("/:owner/:repository/wiki/:page/_revert/:commitId")(collaboratorsOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))
    val Array(from, to) = params("commitId").split("\\.\\.\\.")

    if(revertWikiPage(repository.owner, repository.name, from, to, context.loginAccount.get, Some(pageName))){
      redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}")
    } else {
      flash += "info" -> "This patch was not able to be reversed."
      redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}/_compare/${from}...${to}")
    }
  })

  get("/:owner/:repository/wiki/_revert/:commitId")(collaboratorsOnly { repository =>
    val Array(from, to) = params("commitId").split("\\.\\.\\.")

    if(revertWikiPage(repository.owner, repository.name, from, to, context.loginAccount.get, None)){
      redirect(s"/${repository.owner}/${repository.name}/wiki/")
    } else {
      flash += "info" -> "This patch was not able to be reversed."
      redirect(s"/${repository.owner}/${repository.name}/wiki/_compare/${from}...${to}")
    }
  })

  get("/:owner/:repository/wiki/:page/_edit")(collaboratorsOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))
    wiki.html.edit(pageName, getWikiPage(repository.owner, repository.name, pageName), repository)
  })
  
  post("/:owner/:repository/wiki/_edit", editForm)(collaboratorsOnly { (form, repository) =>
    defining(context.loginAccount.get){ loginAccount =>
      saveWikiPage(repository.owner, repository.name, form.currentPageName, form.pageName,
          form.content, loginAccount, form.message.getOrElse(""), Some(form.id)).map { commitId =>
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
          form.content, loginAccount, form.message.getOrElse(""), None)

      updateLastActivityDate(repository.owner, repository.name)
      recordCreateWikiPageActivity(repository.owner, repository.name, loginAccount.userName, form.pageName)

      redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(form.pageName)}")
    }
  })
  
  get("/:owner/:repository/wiki/:page/_delete")(collaboratorsOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    defining(context.loginAccount.get){ loginAccount =>
      deleteWikiPage(repository.owner, repository.name, pageName, loginAccount.fullName, loginAccount.mailAddress, s"Destroyed ${pageName}")
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
    val path = multiParams("splat").head

    getFileContent(repository.owner, repository.name, path).map { bytes =>
      contentType = FileUtil.getContentType(path, bytes)
      bytes
    } getOrElse NotFound
  })

  private def unique: Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Option[String] =
      getWikiPageList(params("owner"), params("repository")).find(_ == value).map(_ => "Page already exists.")
  }

  private def pagename: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(value.exists("\\/:*?\"<>|".contains(_))){
        Some(s"${name} contains invalid character.")
      } else if(value.startsWith("_") || value.startsWith("-")){
        Some(s"${name} starts with invalid character.")
      } else {
        None
      }
  }

  private def conflictForNew: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      targetWikiPage.map { _ =>
        "Someone has created the wiki since you started. Please reload this page and re-apply your changes."
      }
    }
  }

  private def conflictForEdit: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      targetWikiPage.filter(_.id != params("id")).map{ _ =>
        "Someone has edited the wiki since you started. Please reload this page and re-apply your changes."
      }
    }
  }

  private def targetWikiPage = getWikiPage(params("owner"), params("repository"), params("pageName"))

}
