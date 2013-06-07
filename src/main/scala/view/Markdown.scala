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
             wikiLink: Boolean)(implicit context: app.Context): String = {
    val rootNode = new PegDownProcessor(
      Extensions.AUTOLINKS | Extensions.WIKILINKS | Extensions.FENCED_CODE_BLOCKS | Extensions.TABLES
    ).parseMarkdown(markdown.toCharArray)

    new GitBucketHtmlSerializer(markdown, wikiLink, context, repository).toHtml(rootNode)
  }
}

class GitBucketLinkRender(wikiLink: Boolean, context: app.Context,
                          repository: service.RepositoryService.RepositoryInfo) extends LinkRenderer {
  override def render(node: WikiLinkNode): Rendering = {
    if(wikiLink){
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

//  /**
//   * Converts the issue number and the commit id to the link.
//   */
//  private def markdownFilter(value: String, repository: service.RepositoryService.RepositoryInfo)(implicit context: app.Context): String = {
//    value
//      .replaceAll("#([0-9]+)", "[$0](%s/%s/%s/issue/$1)".format(context.path, repository.owner, repository.name))
//      .replaceAll("[0-9a-f]{40}", "[$0](%s/%s/%s/commit/$0)".format(context.path, repository.owner, repository.name))
//  }

class GitBucketHtmlSerializer(markdown: String, wikiLink: Boolean, context: app.Context, repository: service.RepositoryService.RepositoryInfo)
  extends ToHtmlSerializer(
    new GitBucketLinkRender(wikiLink, context, repository),
    Map[String, VerbatimSerializer](VerbatimSerializer.DEFAULT -> new GitBucketVerbatimSerializer).asJava
  ) {
  override def visit(node: TextNode) {
    // convert commit id to link.
    val text = node.getText.replaceAll("[0-9a-f]{40}",
      "<a href=\"%s/%s/%s/commit/$0\">$0</a>".format(context.path, repository.owner, repository.name))

    // convert issue id to link
    val startIndex = node.getStartIndex
    val text2 = if(startIndex > 0 && markdown.charAt(startIndex - 1) == '#'){
      text.replaceFirst("^([0-9]+)", "<a href=\"%s/%s/%s/issue/$1\">$0</a>".format(context.path, repository.owner, repository.name))
    } else text

    println(text)


    if (abbreviations.isEmpty) {
      printer.print(text2)
    } else {
      printWithAbbreviations(text2)
    }
  }

  override def visit(node: SpecialTextNode) {
    printer.printEncoded(node.getText)
  }
}