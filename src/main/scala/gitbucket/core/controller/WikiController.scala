package gitbucket.core.controller

import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.wiki.html
import gitbucket.core.service.{AccountService, ActivityService, RepositoryService, WikiService}
import gitbucket.core.util._
import gitbucket.core.util.StringUtil._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.Directory._
import io.github.gitbucket.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.scalatra.i18n.Messages

class WikiController extends WikiControllerBase 
  with WikiService with RepositoryService with AccountService with ActivityService
  with CollaboratorsAuthenticator with ReferrerAuthenticator

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
      html.page("Home", page, getWikiPageList(repository.owner, repository.name),
        repository, isEditable(repository),
        getWikiPage(repository.owner, repository.name, "_Sidebar"),
        getWikiPage(repository.owner, repository.name, "_Footer"))
    } getOrElse redirect(s"/${repository.owner}/${repository.name}/wiki/Home/_edit")
  })
  
  get("/:owner/:repository/wiki/:page")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    getWikiPage(repository.owner, repository.name, pageName).map { page =>
      html.page(pageName, page, getWikiPageList(repository.owner, repository.name),
        repository, isEditable(repository),
        getWikiPage(repository.owner, repository.name, "_Sidebar"),
        getWikiPage(repository.owner, repository.name, "_Footer"))
    } getOrElse redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}/_edit")
  })
  
  get("/:owner/:repository/wiki/:page/_history")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
      JGitUtil.getCommitLog(git, "master", path = pageName + ".md") match {
        case Right((logs, hasNext)) => html.history(Some(pageName), logs, repository)
        case Left(_) => NotFound()
      }
    }
  })
  
  get("/:owner/:repository/wiki/:page/_compare/:commitId")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))
    val Array(from, to) = params("commitId").split("\\.\\.\\.")

    using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
      html.compare(Some(pageName), from, to, JGitUtil.getDiffs(git, from, to, true).filter(_.newPath == pageName + ".md"), repository,
        isEditable(repository), flash.get("info"))
    }
  })
  
  get("/:owner/:repository/wiki/_compare/:commitId")(referrersOnly { repository =>
    val Array(from, to) = params("commitId").split("\\.\\.\\.")

    using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
      html.compare(None, from, to, JGitUtil.getDiffs(git, from, to, true), repository,
        isEditable(repository), flash.get("info"))
    }
  })

  get("/:owner/:repository/wiki/:page/_revert/:commitId")(referrersOnly { repository =>
    if(isEditable(repository)){
      val pageName = StringUtil.urlDecode(params("page"))
      val Array(from, to) = params("commitId").split("\\.\\.\\.")

      if(revertWikiPage(repository.owner, repository.name, from, to, context.loginAccount.get, Some(pageName))){
        redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}")
      } else {
        flash += "info" -> "This patch was not able to be reversed."
        redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}/_compare/${from}...${to}")
      }
    } else Unauthorized()
  })

  get("/:owner/:repository/wiki/_revert/:commitId")(referrersOnly { repository =>
    if(isEditable(repository)){
      val Array(from, to) = params("commitId").split("\\.\\.\\.")

      if(revertWikiPage(repository.owner, repository.name, from, to, context.loginAccount.get, None)){
        redirect(s"/${repository.owner}/${repository.name}/wiki/")
      } else {
        flash += "info" -> "This patch was not able to be reversed."
        redirect(s"/${repository.owner}/${repository.name}/wiki/_compare/${from}...${to}")
      }
    } else Unauthorized()
  })

  get("/:owner/:repository/wiki/:page/_edit")(referrersOnly { repository =>
    if(isEditable(repository)){
      val pageName = StringUtil.urlDecode(params("page"))
      html.edit(pageName, getWikiPage(repository.owner, repository.name, pageName), repository)
    } else Unauthorized()
  })
  
  post("/:owner/:repository/wiki/_edit", editForm)(referrersOnly { (form, repository) =>
    if(isEditable(repository)){
      defining(context.loginAccount.get){ loginAccount =>
        saveWikiPage(
          repository.owner,
          repository.name,
          form.currentPageName,
          form.pageName,
          appendNewLine(convertLineSeparator(form.content, "LF"), "LF"),
          loginAccount,
          form.message.getOrElse(""),
          Some(form.id)
        ).map { commitId =>
          updateLastActivityDate(repository.owner, repository.name)
          recordEditWikiPageActivity(repository.owner, repository.name, loginAccount.userName, form.pageName, commitId)
        }
        if(notReservedPageName(form.pageName)) {
          redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(form.pageName)}")
        } else {
          redirect(s"/${repository.owner}/${repository.name}/wiki")
        }
      }
    } else Unauthorized()
  })
  
  get("/:owner/:repository/wiki/_new")(referrersOnly { repository =>
    if(isEditable(repository)){
      html.edit("", None, repository)
    } else Unauthorized()
  })
  
  post("/:owner/:repository/wiki/_new", newForm)(referrersOnly { (form, repository) =>
    if(isEditable(repository)){
      defining(context.loginAccount.get){ loginAccount =>
        saveWikiPage(repository.owner, repository.name, form.currentPageName, form.pageName,
          form.content, loginAccount, form.message.getOrElse(""), None)

        updateLastActivityDate(repository.owner, repository.name)
        recordCreateWikiPageActivity(repository.owner, repository.name, loginAccount.userName, form.pageName)

        if(notReservedPageName(form.pageName)) {
          redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(form.pageName)}")
        } else {
          redirect(s"/${repository.owner}/${repository.name}/wiki")
        }
      }
    } else Unauthorized()
  })
  
  get("/:owner/:repository/wiki/:page/_delete")(referrersOnly { repository =>
    if(isEditable(repository)){
      val pageName = StringUtil.urlDecode(params("page"))

      defining(context.loginAccount.get){ loginAccount =>
        deleteWikiPage(repository.owner, repository.name, pageName, loginAccount.fullName, loginAccount.mailAddress, s"Destroyed ${pageName}")
        updateLastActivityDate(repository.owner, repository.name)

        redirect(s"/${repository.owner}/${repository.name}/wiki")
      }
    } else Unauthorized()
  })
  
  get("/:owner/:repository/wiki/_pages")(referrersOnly { repository =>
    html.pages(getWikiPageList(repository.owner, repository.name), repository, isEditable(repository))
  })
  
  get("/:owner/:repository/wiki/_history")(referrersOnly { repository =>
    using(Git.open(getWikiRepositoryDir(repository.owner, repository.name))){ git =>
      JGitUtil.getCommitLog(git, "master") match {
        case Right((logs, hasNext)) => html.history(None, logs, repository)
        case Left(_) => NotFound()
      }
    }
  })

  get("/:owner/:repository/wiki/_blob/*")(referrersOnly { repository =>
    val path = multiParams("splat").head

    getFileContent(repository.owner, repository.name, path).map { bytes =>
      RawData(FileUtil.getContentType(path, bytes), bytes)
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
      } else if(notReservedPageName(value) && (value.startsWith("_") || value.startsWith("-"))){
        Some(s"${name} starts with invalid character.")
      } else {
        None
      }
  }

  private def notReservedPageName(value: String) = ! (Array[String]("_Sidebar","_Footer") contains value)

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

  private def isEditable(repository: RepositoryInfo)(implicit context: Context): Boolean =
    repository.repository.allowWikiEditing || (
      hasWritePermission(repository.owner, repository.name, context.loginAccount)
    )

}
