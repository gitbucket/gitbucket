package gitbucket.core.view

import gitbucket.core.controller.Context
import gitbucket.core.service.{RepositoryService, RequestCache}
import gitbucket.core.util.Implicits.RichString

trait LinkConverter { self: RequestCache =>

  /**
   * Creates a link to the issue or the pull request from the issue id.
   */
  protected def createIssueLink(repository: RepositoryService.RepositoryInfo, issueId: Int)(implicit context: Context): String = {
    val userName       = repository.repository.userName
    val repositoryName = repository.repository.repositoryName

    getIssue(userName, repositoryName, issueId.toString) match {
      case Some(issue) if (issue.isPullRequest) =>
        s"""<a href="${context.path}/${userName}/${repositoryName}/pull/${issueId}">Pull #${issueId}</a>"""
      case Some(_) =>
        s"""<a href="${context.path}/${userName}/${repositoryName}/issues/${issueId}">Issue #${issueId}</a>"""
      case None =>
        s"Unknown #${issueId}"
    }
  }


  /**
   * Converts issue id, username and commit id to link in the given text.
   */
  protected def convertRefsLinks(text: String, repository: RepositoryService.RepositoryInfo,
                                 issueIdPrefix: String =  "#", escapeHtml: Boolean = true)(implicit context: Context): String = {

    // escape HTML tags
    val escaped = if(escapeHtml) text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;") else text

    escaped
      // convert username/project@SHA to link
      .replaceBy("(?<=(^|\\W))([a-zA-Z0-9\\-_]+)/([a-zA-Z0-9\\-_\\.]+)@([a-f0-9]{40})(?=(\\W|$))".r){ m =>
        getAccountByUserName(m.group(2)).map { _ =>
          s"""<a href="${context.path}/${m.group(2)}/${m.group(3)}/commit/${m.group(4)}">${m.group(2)}/${m.group(3)}@${m.group(4).substring(0, 7)}</a>"""
        }
      }

      // convert username/project#Num to link
      .replaceBy( ("(?<=(^|\\W))([a-zA-Z0-9\\-_]+)/([a-zA-Z0-9\\-_\\.]+)" + issueIdPrefix + "([0-9]+)(?=(\\W|$))").r){ m =>
        getIssue(m.group(2), m.group(3), m.group(4)) match {
          case Some(issue) if (issue.isPullRequest) =>
            Some(s"""<a href="${context.path}/${m.group(2)}/${m.group(3)}/pull/${m.group(4)}">${m.group(2)}/${m.group(3)}#${m.group(4)}</a>""")
          case Some(_) =>
            Some(s"""<a href="${context.path}/${m.group(2)}/${m.group(3)}/issues/${m.group(4)}">${m.group(2)}/${m.group(3)}#${m.group(4)}</a>""")
          case None =>
            Some(s"""${m.group(2)}/${m.group(3)}#${m.group(4)}""")
        }
      }

      // convert username@SHA to link
      .replaceBy( ("(?<=(^|\\W))([a-zA-Z0-9\\-_]+)@([a-f0-9]{40})(?=(\\W|$))").r ) { m =>
        getAccountByUserName(m.group(2)).map { _ =>
          s"""<a href="${context.path}/${m.group(2)}/${repository.name}/commit/${m.group(3)}">${m.group(2)}@${m.group(3).substring(0, 7)}</a>"""
        }
      }

      // convert username#Num to link
      .replaceBy( ("(?<=(^|\\W))([a-zA-Z0-9\\-_]+)" + issueIdPrefix + "([0-9]+)(?=(\\W|$))").r ) { m =>
        getIssue(m.group(2), repository.name, m.group(3)) match {
          case Some(issue) if(issue.isPullRequest) =>
            Some(s"""<a href="${context.path}/${m.group(2)}/${repository.name}/pull/${m.group(3)}">${m.group(2)}#${m.group(3)}</a>""")
          case Some(_) =>
            Some(s"""<a href="${context.path}/${m.group(2)}/${repository.name}/issues/${m.group(3)}">${m.group(2)}#${m.group(3)}</a>""")
          case None =>
            Some(s"""${m.group(2)}#${m.group(3)}""")
        }
      }

      // convert issue id to link
      .replaceBy(("(?<=(^|\\W))(GH-|" + issueIdPrefix + ")([0-9]+)(?=(\\W|$))").r){ m =>
        val prefix = if(m.group(2) == "issue:") "#" else m.group(2)
        getIssue(repository.owner, repository.name, m.group(3)) match {
          case Some(issue) if(issue.isPullRequest) =>
            Some(s"""<a href="${context.path}/${repository.owner}/${repository.name}/pull/${m.group(3)}">${prefix}${m.group(3)}</a>""")
          case Some(_) =>
            Some(s"""<a href="${context.path}/${repository.owner}/${repository.name}/issues/${m.group(3)}">${prefix}${m.group(3)}</a>""")
          case None =>
            Some(s"""${m.group(2)}${m.group(3)}""")
        }
      }

      // convert @username to link
      .replaceBy("(?<=(^|\\W))@([a-zA-Z0-9\\-_\\.]+)(?=(\\W|$))".r){ m =>
        getAccountByUserName(m.group(2)).map { _ =>
          s"""<a href="${context.path}/${m.group(2)}">@${m.group(2)}</a>"""
        }
      }

      // convert commit id to link
      .replaceAll("(?<=(^|[^\\w/@]))([a-f0-9]{40})(?=(\\W|$))", s"""<a href="${context.path}/${repository.owner}/${repository.name}/commit/$$2">$$2</a>""")
  }
}
