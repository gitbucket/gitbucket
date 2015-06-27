package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService
import gitbucket.core.view.Markdown
import play.twirl.api.Html

/**
 * A render engine to render content to HTML.
 */
trait Renderer {

  /**
   * Render the given request to HTML.
   */
  def render(request: RenderRequest): Html

}

object MarkdownRenderer extends Renderer {
  override def render(request: RenderRequest): Html = {
    import request._
    Html(Markdown.toHtml(fileContent, repository, enableWikiLink, enableRefsLink, enableAnchor)(context))
  }
}

object DefaultRenderer extends Renderer {
  override def render(request: RenderRequest): Html = {
    import request._
    Html(
      s"<tt>${
        fileContent.split("(\\r\\n)|\\n").map(xml.Utility.escape(_)).mkString("<br/>")
      }</tt>"
    )
  }
}

case class RenderRequest(filePath: List[String],
   fileContent: String,
   branch: String,
   repository: RepositoryService.RepositoryInfo,
   enableWikiLink: Boolean,
   enableRefsLink: Boolean,
   enableAnchor: Boolean,
   context: Context)