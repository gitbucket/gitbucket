package view

import service.RequestCache
import util.Implicits.RichString

trait LinkConverter { self: RequestCache =>

  /**
   * Converts issue id, username and commit id to link.
   */
  protected def convertRefsLinks(value: String, repository: service.RepositoryService.RepositoryInfo,
                                 issueIdPrefix: String =  "#")(implicit context: app.Context): String = {
    value
      // escape HTML tags
      .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;")
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
