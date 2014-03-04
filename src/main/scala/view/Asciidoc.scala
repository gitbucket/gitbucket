package view

import org.asciidoctor.Asciidoctor
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.OptionsBuilder
import org.asciidoctor.SafeMode
import org.htmlcleaner.HtmlCleaner
import org.htmlcleaner.HtmlNode
import org.htmlcleaner.SimpleHtmlSerializer
import org.htmlcleaner.TagNode
import org.htmlcleaner.TagNodeVisitor

object Asciidoc {

  private[this] lazy val asciidoctor = Asciidoctor.Factory.create()

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def toHtml(asciidoc: String, branch: String, repository: service.RepositoryService.RepositoryInfo,
             enableWikiLink: Boolean, enableRefsLink: Boolean)(implicit context: app.Context): String = {

    val options = OptionsBuilder.options()
    options.safe(SafeMode.SECURE)
    val attributes = AttributesBuilder.attributes()
    attributes.showTitle(true)
    options.attributes(attributes.get())
    val rendered = asciidoctor.render(asciidoc, options)

    // this is always relative to the base dir of the repo, as we currently only render README files.
    val relativeUrlPrefix = s"${helpers.url(repository)}/blob/${branch}/"
    prefixRelativeUrls(rendered, relativeUrlPrefix)
  }

  def prefixRelativeUrls(html: String, urlPrefix: String): String = {
    val cleaner = new HtmlCleaner()
    val node = cleaner.clean(html)
    node.traverse(new TagNodeVisitor() {
      override def visit(tagNode: TagNode, htmlNode: HtmlNode): Boolean = {
        htmlNode match {
          case tag: TagNode if tag.getName == "a" =>
            Option(tag.getAttributeByName("href")) foreach { href =>
              if (!href.startsWith("/") && !href.startsWith("http://") && !href.startsWith("https://")) {
                tag.addAttribute("href", s"${urlPrefix}${href}")
              }
            }
          case _ =>
        }
        // continue traversal
        true
      }
    })
    new SimpleHtmlSerializer(cleaner.getProperties()).getAsString(node)
  }

}

