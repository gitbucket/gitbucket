package gitbucket.core.view

import gitbucket.core.controller.Context
import gitbucket.core.service.{RepositoryService, RequestCache}
import gitbucket.core.util.Implicits.RichString

trait LinkConverter { self: RequestCache =>

  /**
   * Converts issue id, username and commit id to link.
   */
  protected def convertRefsLinks(value: String, repository: RepositoryService.RepositoryInfo,
      issueIdPrefix: String =  "#", escapeHtml: Boolean = true)(implicit context: Context): String = {

    // escape HTML tags
    val escaped = if(escapeHtml) value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;") else value

    escaped
      // convert issue id to link
      .replaceBy(("(?<=(^|\\W))" + issueIdPrefix + "([0-9]+)(?=(\\W|$))").r){ m =>
        getIssue(repository.owner, repository.name, m.group(2)) match {
          case Some(issue) if(issue.isPullRequest)
                       => Some(s"""<a href="${context.path}/${repository.owner}/${repository.name}/pull/${m.group(2)}">#${m.group(2)}</a>""")
          case Some(_) => Some(s"""<a href="${context.path}/${repository.owner}/${repository.name}/issues/${m.group(2)}">#${m.group(2)}</a>""")
          case None    => Some(s"""#${m.group(2)}""")
        }
      }
      // convert @username to link
      .replaceBy("(?<=(^|\\W))@([a-zA-Z0-9\\-_]+)(?=(\\W|$))".r){ m =>
        getAccountByUserName(m.group(2)).map { _ =>
          s"""<a href="${context.path}/${m.group(2)}">@${m.group(2)}</a>"""
        }
      }
      // convert commit id to link
      .replaceAll("(?<=(^|\\W))([a-f0-9]{40})(?=(\\W|$))", s"""<a href="${context.path}/${repository.owner}/${repository.name}/commit/$$2">$$2</a>""")
  }
}
