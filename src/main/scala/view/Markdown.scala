package view

import util.StringUtil
import util.Implicits._
import org.parboiled.common.StringUtils
import org.pegdown._
import org.pegdown.ast._
import org.pegdown.LinkRenderer.Rendering
import scala.collection.JavaConverters._
import service.RequestCache

object Markdown {

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def toHtml(markdown: String, repository: service.RepositoryService.RepositoryInfo,
             enableWikiLink: Boolean, enableRefsLink: Boolean)(implicit context: app.Context): String = {
    // escape issue id
    val source = if(enableRefsLink){
      markdown.replaceAll("(^|\\W)#([0-9]+)(\\W|$)", "$1issue:$2$3")
    } else markdown

    val rootNode = new PegDownProcessor(
      Extensions.AUTOLINKS | Extensions.WIKILINKS | Extensions.FENCED_CODE_BLOCKS | Extensions.TABLES
    ).parseMarkdown(source.toCharArray)

    new GitBucketHtmlSerializer(markdown, repository, enableWikiLink, enableRefsLink).toHtml(rootNode)
  }
}

class GitBucketLinkRender(context: app.Context, repository: service.RepositoryService.RepositoryInfo,
                          enableWikiLink: Boolean) extends LinkRenderer {
  override def render(node: WikiLinkNode): Rendering = {
    if(enableWikiLink){
      try {
        val text = node.getText
        val (label, page) = if(text.contains('|')){
          val i = text.indexOf('|')
          (text.substring(0, i), text.substring(i + 1))
        } else {
          (text, text)
        }
        val url = repository.url.replaceFirst("/git/", "/").replaceFirst("\\.git$", "") + "/wiki/" + StringUtil.urlEncode(page)
        new Rendering(url, label)
      } catch {
        case e: java.io.UnsupportedEncodingException => throw new IllegalStateException
      }
    } else {
      super.render(node)
    }
  }
}

class GitBucketVerbatimSerializer extends VerbatimSerializer {
  def serialize(node: VerbatimNode, printer: Printer) {
    printer.println.print("<pre")
    if (!StringUtils.isEmpty(node.getType)) {
      printer.print(" class=").print('"').print("prettyprint ").print(node.getType).print('"')
    }
    printer.print(">")
    var text: String = node.getText
    while (text.charAt(0) == '\n') {
      printer.print("<br/>")
      text = text.substring(1)
    }
    printer.printEncoded(text)
    printer.print("</pre>")
  }
}

class GitBucketHtmlSerializer(
    markdown: String,
    repository: service.RepositoryService.RepositoryInfo,
    enableWikiLink: Boolean,
    enableRefsLink: Boolean
  )(implicit val context: app.Context) extends ToHtmlSerializer(
    new GitBucketLinkRender(context, repository, enableWikiLink),
    Map[String, VerbatimSerializer](VerbatimSerializer.DEFAULT -> new GitBucketVerbatimSerializer).asJava
  ) with LinkConverter with RequestCache {

  override protected def printImageTag(imageNode: SuperNode, url: String): Unit =
    printer.print("<img src=\"").print(fixUrl(url)).print("\"  alt=\"").printEncoded(printChildrenToString(imageNode)).print("\"/>")

  override protected def printLink(rendering: LinkRenderer.Rendering): Unit = {
    printer.print('<').print('a')
    printAttribute("href", fixUrl(rendering.href))
    for (attr <- rendering.attributes.asScala) {
      printAttribute(attr.name, attr.value)
    }
    printer.print('>').print(rendering.text).print("</a>")
  }

  private def fixUrl(url: String): String = {
    if(!enableWikiLink || url.startsWith("http://") || url.startsWith("https://")){
      url
    } else {
      repository.url.replaceFirst("/git/", "/").replaceFirst("\\.git$", "") + "/wiki/_blob/" + url
    }
  }

  private def printAttribute(name: String, value: String) {
    printer.print(' ').print(name).print('=').print('"').print(value).print('"')
  }

  override def visit(node: TextNode) {
    // convert commit id and username to link.
    val text = if(enableRefsLink) convertRefsLinks(node.getText, repository, "issue:") else node.getText

    if (abbreviations.isEmpty) {
      printer.print(text)
    } else {
      printWithAbbreviations(text)
    }
  }

}