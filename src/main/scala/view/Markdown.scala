package view

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
      super.render(node)
    } else {
      try {
        val text = node.getText
        val (label, page) = if(text.contains('|')){
          val i = text.indexOf('|')
          (text.substring(0, i), text.substring(i + 1))
        } else {
          (text, text)
        }
        val url = "%s/%s/%s/wiki/%s".format(context.path, repository.owner, repository.name,
          java.net.URLEncoder.encode(page.replace(' ', '-'), "UTF-8"))
        new Rendering(url, label)
      } catch {
        case e: java.io.UnsupportedEncodingException => throw new IllegalStateException();
      }
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

  override def toHtml(rootNode: RootNode): String = {
    val html = super.toHtml(rootNode)
    if(enableIssueLink){
      // convert marked issue id to link.
      html.replaceAll("#\\{\\{\\{\\{([0-9]+)\\}\\}\\}\\}",
        "<a href=\"%s/%s/%s/issue/$1\">#$1</a>".format(context.path, repository.owner, repository.name))
    } else html
  }

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
    if(!enableWikiLink || url.startsWith("http://") || url.startsWith("https://")) url
    else repository.url.replaceFirst("/git/", "/").replaceFirst("\\.git$", "") + "/wiki/_blob/" + url
  }

  private def printAttribute(name: String, value: String) {
    printer.print(' ').print(name).print('=').print('"').print(value).print('"')
  }

  override def visit(node: TextNode) {
    // convert commit id to link.
    val text1 = if(enableCommitLink) node.getText.replaceAll("[0-9a-f]{40}",
      "<a href=\"%s/%s/%s/commit/$0\">$0</a>".format(context.path, repository.owner, repository.name))
    else node.getText

    // mark issue id to link
    val startIndex = node.getStartIndex
    val text2 = if(enableIssueLink && startIndex > 0 && markdown.charAt(startIndex - 1) == '#'){
      text1.replaceFirst("^([0-9]+)", "{{{{$0}}}}")
    } else text1

    if (abbreviations.isEmpty) {
      printer.print(text2)
    } else {
      printWithAbbreviations(text2)
    }
  }

  override def visit(node: HeaderNode) {
    if(enableIssueLink && markdown.substring(node.getStartIndex, node.getEndIndex - 1).startsWith("#")){
      printer.print("#" * node.getLevel)
      visitChildren(node)
    } else {
      printTag(node, "h" + node.getLevel)
    }
  }

}