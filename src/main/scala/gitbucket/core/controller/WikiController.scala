package gitbucket.core.controller

import gitbucket.core.model.WebHook
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.WebHookService.WebHookGollumPayload
import gitbucket.core.wiki.html
import gitbucket.core.service._
import gitbucket.core.util._
import gitbucket.core.util.StringUtil._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.Directory._
import org.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.scalatra.i18n.Messages
import scala.util.Using

class WikiController
    extends WikiControllerBase
    with WikiService
    with RepositoryService
    with AccountService
    with ActivityService
    with WebHookService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator

trait WikiControllerBase extends ControllerBase {
  self: WikiService
    with RepositoryService
    with AccountService
    with ActivityService
    with WebHookService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator =>

  case class WikiPageEditForm(
    pageName: String,
    content: String,
    message: Option[String],
    currentPageName: String,
    id: String
  )

  val newForm = mapping(
    "pageName" -> trim(label("Page name", text(required, maxlength(40), pagename, unique))),
    "content" -> trim(label("Content", text(required, conflictForNew))),
    "message" -> trim(label("Message", optional(text()))),
    "currentPageName" -> trim(label("Current page name", text())),
    "id" -> trim(label("Latest commit id", text()))
  )(WikiPageEditForm.apply)

  val editForm = mapping(
    "pageName" -> trim(label("Page name", text(required, maxlength(40), pagename))),
    "content" -> trim(label("Content", text(required, conflictForEdit))),
    "message" -> trim(label("Message", optional(text()))),
    "currentPageName" -> trim(label("Current page name", text(required))),
    "id" -> trim(label("Latest commit id", text(required)))
  )(WikiPageEditForm.apply)

  get("/:owner/:repository/wiki")(referrersOnly { repository =>
    getWikiPage(repository.owner, repository.name, "Home").map { page =>
      html.page(
        "Home",
        page,
        getWikiPageList(repository.owner, repository.name),
        repository,
        isEditable(repository),
        getWikiPage(repository.owner, repository.name, "_Sidebar"),
        getWikiPage(repository.owner, repository.name, "_Footer")
      )
    } getOrElse redirect(s"/${repository.owner}/${repository.name}/wiki/Home/_edit")
  })

  get("/:owner/:repository/wiki/:page")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    getWikiPage(repository.owner, repository.name, pageName).map { page =>
      html.page(
        pageName,
        page,
        getWikiPageList(repository.owner, repository.name),
        repository,
        isEditable(repository),
        getWikiPage(repository.owner, repository.name, "_Sidebar"),
        getWikiPage(repository.owner, repository.name, "_Footer")
      )
    } getOrElse redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}/_edit")
  })

  get("/:owner/:repository/wiki/:page/_history")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))

    Using.resource(Git.open(getWikiRepositoryDir(repository.owner, repository.name))) { git =>
      JGitUtil.getCommitLog(git, "master", path = pageName + ".md") match {
        case Right((logs, hasNext)) => html.history(Some(pageName), logs, repository, isEditable(repository))
        case Left(_)                => NotFound()
      }
    }
  })

  get("/:owner/:repository/wiki/:page/_compare/:commitId")(referrersOnly { repository =>
    val pageName = StringUtil.urlDecode(params("page"))
    val Array(from, to) = params("commitId").split("\\.\\.\\.")

    Using.resource(Git.open(getWikiRepositoryDir(repository.owner, repository.name))) { git =>
      html.compare(
        Some(pageName),
        from,
        to,
        JGitUtil.getDiffs(git, Some(from), to, true, false).filter(_.newPath == pageName + ".md"),
        repository,
        isEditable(repository),
        flash.get("info")
      )
    }
  })

  get("/:owner/:repository/wiki/_compare/:commitId")(referrersOnly { repository =>
    val Array(from, to) = params("commitId").split("\\.\\.\\.")

    Using.resource(Git.open(getWikiRepositoryDir(repository.owner, repository.name))) { git =>
      html.compare(
        None,
        from,
        to,
        JGitUtil.getDiffs(git, Some(from), to, true, false),
        repository,
        isEditable(repository),
        flash.get("info")
      )
    }
  })

  get("/:owner/:repository/wiki/:page/_revert/:commitId")(readableUsersOnly { repository =>
    if (isEditable(repository)) {
      val pageName = StringUtil.urlDecode(params("page"))
      val Array(from, to) = params("commitId").split("\\.\\.\\.")

      if (revertWikiPage(repository.owner, repository.name, from, to, context.loginAccount.get, Some(pageName))) {
        redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}")
      } else {
        flash.update("info", "This patch was not able to be reversed.")
        redirect(
          s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(pageName)}/_compare/${from}...${to}"
        )
      }
    } else Unauthorized()
  })

  get("/:owner/:repository/wiki/_revert/:commitId")(readableUsersOnly { repository =>
    if (isEditable(repository)) {
      val Array(from, to) = params("commitId").split("\\.\\.\\.")

      if (revertWikiPage(repository.owner, repository.name, from, to, context.loginAccount.get, None)) {
        redirect(s"/${repository.owner}/${repository.name}/wiki")
      } else {
        flash.update("info", "This patch was not able to be reversed.")
        redirect(s"/${repository.owner}/${repository.name}/wiki/_compare/${from}...${to}")
      }
    } else Unauthorized()
  })

  get("/:owner/:repository/wiki/:page/_edit")(readableUsersOnly { repository =>
    if (isEditable(repository)) {
      val pageName = StringUtil.urlDecode(params("page"))
      html.edit(pageName, getWikiPage(repository.owner, repository.name, pageName), repository)
    } else Unauthorized()
  })

  post("/:owner/:repository/wiki/_edit", editForm)(readableUsersOnly { (form, repository) =>
    if (isEditable(repository)) {
      defining(context.loginAccount.get) {
        loginAccount =>
          saveWikiPage(
            repository.owner,
            repository.name,
            form.currentPageName,
            form.pageName,
            appendNewLine(convertLineSeparator(form.content, "LF"), "LF"),
            loginAccount,
            form.message.getOrElse(""),
            Some(form.id)
          ).foreach {
            commitId =>
              updateLastActivityDate(repository.owner, repository.name)
              recordEditWikiPageActivity(
                repository.owner,
                repository.name,
                loginAccount.userName,
                form.pageName,
                commitId
              )
              callWebHookOf(repository.owner, repository.name, WebHook.Gollum, context.settings) {
                getAccountByUserName(repository.owner).map { repositoryUser =>
                  WebHookGollumPayload("edited", form.pageName, commitId, repository, repositoryUser, loginAccount)
                }
              }
          }
          if (notReservedPageName(form.pageName)) {
            redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(form.pageName)}")
          } else {
            redirect(s"/${repository.owner}/${repository.name}/wiki")
          }
      }
    } else Unauthorized()
  })

  get("/:owner/:repository/wiki/_new")(readableUsersOnly { repository =>
    if (isEditable(repository)) {
      html.edit("", None, repository)
    } else Unauthorized()
  })

  post("/:owner/:repository/wiki/_new", newForm)(readableUsersOnly { (form, repository) =>
    if (isEditable(repository)) {
      defining(context.loginAccount.get) {
        loginAccount =>
          saveWikiPage(
            repository.owner,
            repository.name,
            form.currentPageName,
            form.pageName,
            form.content,
            loginAccount,
            form.message.getOrElse(""),
            None
          ).foreach {
            commitId =>
              updateLastActivityDate(repository.owner, repository.name)
              recordCreateWikiPageActivity(repository.owner, repository.name, loginAccount.userName, form.pageName)
              callWebHookOf(repository.owner, repository.name, WebHook.Gollum, context.settings) {
                getAccountByUserName(repository.owner).map { repositoryUser =>
                  WebHookGollumPayload("created", form.pageName, commitId, repository, repositoryUser, loginAccount)
                }
              }
          }

          if (notReservedPageName(form.pageName)) {
            redirect(s"/${repository.owner}/${repository.name}/wiki/${StringUtil.urlEncode(form.pageName)}")
          } else {
            redirect(s"/${repository.owner}/${repository.name}/wiki")
          }
      }
    } else Unauthorized()
  })

  get("/:owner/:repository/wiki/:page/_delete")(readableUsersOnly { repository =>
    if (isEditable(repository)) {
      val pageName = StringUtil.urlDecode(params("page"))

      defining(context.loginAccount.get) { loginAccount =>
        deleteWikiPage(
          repository.owner,
          repository.name,
          pageName,
          loginAccount.fullName,
          loginAccount.mailAddress,
          s"Destroyed ${pageName}"
        )
        updateLastActivityDate(repository.owner, repository.name)

        redirect(s"/${repository.owner}/${repository.name}/wiki")
      }
    } else Unauthorized()
  })

  get("/:owner/:repository/wiki/_pages")(referrersOnly { repository =>
    html.pages(getWikiPageList(repository.owner, repository.name), repository, isEditable(repository))
  })

  get("/:owner/:repository/wiki/_history")(referrersOnly { repository =>
    Using.resource(Git.open(getWikiRepositoryDir(repository.owner, repository.name))) { git =>
      JGitUtil.getCommitLog(git, "master") match {
        case Right((logs, hasNext)) => html.history(None, logs, repository, isEditable(repository))
        case Left(_)                => NotFound()
      }
    }
  })

  get("/:owner/:repository/wiki/_blob/*")(referrersOnly { repository =>
    val path = multiParams("splat").head
    Using.resource(Git.open(getWikiRepositoryDir(repository.owner, repository.name))) { git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve("master"))

      getPathObjectId(git, path, revCommit).map { objectId =>
        responseRawFile(git, objectId, path, repository)
      } getOrElse NotFound()
    }
  })

  private def unique: Constraint = new Constraint() {
    override def validate(
      name: String,
      value: String,
      params: Map[String, Seq[String]],
      messages: Messages
    ): Option[String] =
      getWikiPageList(params.value("owner"), params.value("repository"))
        .find(_ == value)
        .map(_ => "Page already exists.")
  }

  private def pagename: Constraint = new Constraint() {
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if (value.exists("\\/:*?\"<>|".contains(_))) {
        Some(s"${name} contains invalid character.")
      } else if (notReservedPageName(value) && (value.startsWith("_") || value.startsWith("-"))) {
        Some(s"${name} starts with invalid character.")
      } else {
        None
      }
  }

  private def notReservedPageName(value: String) = !(Array[String]("_Sidebar", "_Footer") contains value)

  private def conflictForNew: Constraint = new Constraint() {
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      targetWikiPage.map { _ =>
        "Someone has created the wiki since you started. Please reload this page and re-apply your changes."
      }
    }
  }

  private def conflictForEdit: Constraint = new Constraint() {
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      targetWikiPage.filter(_.id != params("id")).map { _ =>
        "Someone has edited the wiki since you started. Please reload this page and re-apply your changes."
      }
    }
  }

  private def targetWikiPage = getWikiPage(params("owner"), params("repository"), params("pageName"))

  private def isEditable(repository: RepositoryInfo)(implicit context: Context): Boolean = {
    repository.repository.options.wikiOption match {
      case "ALL"     => !repository.repository.isPrivate && context.loginAccount.isDefined
      case "PUBLIC"  => hasGuestRole(repository.owner, repository.name, context.loginAccount)
      case "PRIVATE" => hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
      case "DISABLE" => false
    }
  }

}
