package view

import java.util.{ Locale, Date, TimeZone }
import play.twirl.api.Html
import service.RepositoryService

case class RenderRequest(filePath: List[String],
  fileContent: String,
  branch: String,
  repository: RepositoryService.RepositoryInfo,
  enableWikiLink: Boolean,
  enableRefsLink: Boolean,
  context: app.Context)

/**
 * A render engine to render content to HTML.
 */
trait Renderer {
  /**
   * Returns `true`, it the engine is enabled and thus can be used for rendering.
   */
  def enabled: Boolean
  /**
   * Returns the supported file suffixes (extensions).
   */
  def supportedSuffixes: Seq[String]
  /**
   * Render the given request to HTML.
   */
  def render(request: RenderRequest): Html
}

/**
 * Renderer for Markdown formatted content.
 */
object MarkdownRenderer extends Renderer {
  override def enabled: Boolean = true
  override def supportedSuffixes: Seq[String] = Seq(".md", ".markdown")
  override def render(request: RenderRequest): Html = {
    import request._
    Html(Markdown.toHtml(fileContent, repository, enableWikiLink, enableRefsLink)(context))
  }
}

/**
 * Renderer for Asciidoc formatted content.
 */
object AsciidocRenderer extends Renderer {
  /**
   * This renderer support the runtime detection of some required classes.
   * Missing classes mean the renderer will be deactivated.
   */
  override val enabled: Boolean = try {
    val c1 = classOf[org.asciidoctor.Asciidoctor]
    val c2 = classOf[org.htmlcleaner.SimpleHtmlSerializer]
    val c3 = classOf[org.slf4j.LoggerFactory]
    val c4 = classOf[org.jruby.RubyInstanceConfig]
    true
  } catch {
    case c: NoClassDefFoundError =>
      Console.err.println("Deactivating AsciidocRenderer support. Missing class: " + c.getMessage)
      false
  }
  override def supportedSuffixes: Seq[String] = Seq(".adoc", ".asciidoc")
  override def render(request: RenderRequest): Html = {
    import request._
    Html(Asciidoc.toHtml(filePath, fileContent, branch, repository, enableWikiLink, enableRefsLink)(context))
  }
}
