package gitbucket.core.view

import gitbucket.core.controller.Context
import gitbucket.core.service.{RepositoryService, RequestCache}
import gitbucket.core.util.Implicits.RichString
import gitbucket.core.util.StringUtil

trait LinkConverter { self: RequestCache =>

  /**
   * Creates a link to the issue or the pull request from the issue id.
   */
  protected def createIssueLink(repository: RepositoryService.RepositoryInfo, issueId: Int, title: String)(
    implicit context: Context
  ): String = {
    val userName = repository.repository.userName
    val repositoryName = repository.repository.repositoryName

    getIssueFromCache(userName, repositoryName, issueId.toString) match {
      case Some(issue) =>
        s"""<a href="${context.path}/${userName}/${repositoryName}/${if (issue.isPullRequest) "pull" else "issues"}/${issueId}"><strong>${StringUtil
          .escapeHtml(title)}</strong> #${issueId}</a>"""
      case None =>
        s"Unknown #${issueId}"
    }
  }

  /**
   * Converts issue id, username and commit id to link in the given text.
   */
  protected def convertRefsLinks(
    text: String,
    repository: RepositoryService.RepositoryInfo,
    issueIdPrefix: String = "#",
    escapeHtml: Boolean = true
  )(implicit context: Context): String = {

    // escape HTML tags
    val escaped =
      if (escapeHtml) text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
      else text

    escaped
    // convert username/project@SHA to link
      .replaceBy("(?<=(^|\\W))([a-zA-Z0-9\\-_]+)/([a-zA-Z0-9\\-_\\.]+)@([a-f0-9]{40})(?=(\\W|$))".r) { m =>
        getAccountByUserNameFromCache(m.group(2)).map { _ =>
          s"""<code><a href="${context.path}/${m.group(2)}/${m.group(3)}/commit/${m.group(4)}">${m.group(2)}/${m.group(
            3
          )}@${m.group(4).substring(0, 7)}</a></code>"""
        }
      }

      // convert username/project#Num to link
      .replaceBy(("(?<=(^|\\W))([a-zA-Z0-9\\-_]+)/([a-zA-Z0-9\\-_\\.]+)" + issueIdPrefix + "([0-9]+)(?=(\\W|$))").r) {
        m =>
          getIssueFromCache(m.group(2), m.group(3), m.group(4)) match {
            case Some(pull) if (pull.isPullRequest) =>
              Some(s"""<a href="${context.path}/${m.group(2)}/${m.group(3)}/pull/${m
                .group(4)}" title="${pull.title}">${m.group(2)}/${m.group(
                3
              )}#${m.group(4)}</a>""")
            case Some(issue) =>
              Some(s"""<a href="${context.path}/${m.group(2)}/${m.group(3)}/issues/${m
                .group(4)}" title="${issue.title}">${m.group(2)}/${m
                .group(3)}#${m.group(4)}</a>""")
            case None =>
              Some(s"""${m.group(2)}/${m.group(3)}#${m.group(4)}""")
          }
      }

      // convert username@SHA to link
      .replaceBy(("(?<=(^|\\W))([a-zA-Z0-9\\-_]+)@([a-f0-9]{40})(?=(\\W|$))").r) { m =>
        getAccountByUserNameFromCache(m.group(2)).map { _ =>
          s"""<code><a href="${context.path}/${m.group(2)}/${repository.name}/commit/${m.group(3)}">${m.group(2)}@${m
            .group(3)
            .substring(0, 7)}</a></code>"""
        }
      }

      // convert username#Num to link
      .replaceBy(("(?<=(^|\\W))([a-zA-Z0-9\\-_]+)" + issueIdPrefix + "([0-9]+)(?=(\\W|$))").r) { m =>
        getIssueFromCache(m.group(2), repository.name, m.group(3)) match {
          case Some(issue) if (issue.isPullRequest) =>
            Some(s"""<a href="${context.path}/${m.group(2)}/${repository.name}/pull/${m.group(3)}">${m.group(2)}#${m
              .group(3)}</a>""")
          case Some(_) =>
            Some(s"""<a href="${context.path}/${m.group(2)}/${repository.name}/issues/${m.group(3)}">${m.group(2)}#${m
              .group(3)}</a>""")
          case None =>
            Some(s"""${m.group(2)}#${m.group(3)}""")
        }
      }

      // convert issue id to link
      .replaceBy(("(?<=(^|\\W))(GH-|(?<!&)" + issueIdPrefix + ")([0-9]+)(?=(\\W|$))").r) { m =>
        val prefix = if (m.group(2) == "issue:") "#" else m.group(2)
        getIssueFromCache(repository.owner, repository.name, m.group(3)) match {
          case Some(pull) if (pull.isPullRequest) =>
            Some(s"""<a href="${context.path}/${repository.owner}/${repository.name}/pull/${m
              .group(3)}" title="${pull.title}">${prefix}${m
              .group(3)}</a>""")
          case Some(issue) =>
            Some(s"""<a href="${context.path}/${repository.owner}/${repository.name}/issues/${m
              .group(3)}"  title="${issue.title}">${prefix}${m
              .group(3)}</a>""")
          case None =>
            Some(s"""${m.group(2)}${m.group(3)}""")
        }
      }

      // convert @username to link
      .replaceBy("(?<=(^|\\W))@([a-zA-Z0-9\\-_\\.]+)(?=(\\W|$))".r) { m =>
        getAccountByUserNameFromCache(m.group(2)).map { _ =>
          s"""<a href="${context.path}/${m.group(2)}">@${m.group(2)}</a>"""
        }
      }

      // convert commit id to link
      .replaceBy("(?<=(^|[^\\w/@]))([a-f0-9]{40})(?=(\\W|$))".r) { m =>
        Some(s"""<code><a href="${context.path}/${repository.owner}/${repository.name}/commit/${m
          .group(2)}">${m.group(2).substring(0, 7)}</a></code>""")
      }
  }
}
