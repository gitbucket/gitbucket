package view

import util.StringUtil
import org.parboiled.common.StringUtils
import org.pegdown._
import org.pegdown.ast._
import org.pegdown.LinkRenderer.Rendering
import scala.collection.JavaConverters._

object Markdown {

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def toHtml(markdown: String, repository: service.RepositoryService.RepositoryInfo,
             enableWikiLink: Boolean, enableCommitLink: Boolean, enableIssueLink: Boolean)(implicit context: app.Context): String = {
    val rootNode = new PegDownProcessor(
      Extensions.AUTOLINKS | Extensions.WIKILINKS | Extensions.FENCED_CODE_BLOCKS | Extensions.TABLES
    ).parseMarkdown(markdown.toCharArray)

    new GitBucketHtmlSerializer(markdown, context, repository, enableWikiLink, enableCommitLink, enableIssueLink).toHtml(rootNode)
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
    context: app.Context,
    repository: service.RepositoryService.RepositoryInfo,
    enableWikiLink: Boolean,
    enableCommitLink: Boolean,
    enableIssueLink: Boolean
  ) extends ToHtmlSerializer(
    new GitBucketLinkRender(context, repository, enableWikiLink),
    Map[String, VerbatimSerializer](VerbatimSerializer.DEFAULT -> new GitBucketVerbatimSerializer).asJava
  ) {

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
    val text = if(enableCommitLink) node.getText
      .replaceAll("(^|\\W)([0-9a-f]{40})(\\W|$)", s"""$$1<a href="${context.path}/${repository.owner}/${repository.name}/commit/$$2">$$2</a>$$3""")
      .replaceAll("(^|\\W)@([a-zA-Z0-9\\-_]+)(\\W|$)", s"""$$1<a href="${context.path}/$$2">@$$2</a>$$3""")
    else node.getText

    println(text)

    if (abbreviations.isEmpty) {
      printer.print(text)
    } else {
      printWithAbbreviations(text)
    }
  }

  override def visit(node: HeaderNode) {
    val text = markdown.substring(node.getStartIndex, node.getEndIndex - 1).trim
    if(enableIssueLink && text.matches("#[\\d]+")){
      // convert issue id to link
      val issueId = text.substring(1).toInt
      printer.print(s"""<a href="${context.path}/${repository.owner}/${repository.name}/issues/${issueId}">#${issueId}</a>""")
    } else {
      printTag(node, "h" + node.getLevel)
    }
  }

}