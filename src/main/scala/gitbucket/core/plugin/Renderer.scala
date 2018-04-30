package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService
import gitbucket.core.view.{Markdown, helpers}
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
    Html(
      Markdown.toHtml(
        markdown = fileContent,
        repository = repository,
        enableWikiLink = enableWikiLink,
        enableRefsLink = enableRefsLink,
        enableAnchor = enableAnchor,
        enableLineBreaks = false
      )(context)
    )
  }
}

case class RenderRequest(
  filePath: List[String],
  fileContent: String,
  branch: String,
  repository: RepositoryService.RepositoryInfo,
  enableWikiLink: Boolean,
  enableRefsLink: Boolean,
  enableAnchor: Boolean,
  context: Context
) {
  val rawPath = s"""${helpers.url(repository)(context)}/raw/${branch}/${filePath.mkString("/")}"""
}
