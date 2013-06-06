package view

import org.parboiled.common.StringUtils
import org.pegdown._
import org.pegdown.LinkRenderer.Rendering
import org.pegdown.ast.{WikiLinkNode, VerbatimNode}
import scala.collection.JavaConverters._

object Markdown {

//  /**
//   * Converts the issue number and the commit id to the link.
//   */
//  private def markdownFilter(value: String, repository: service.RepositoryService.RepositoryInfo)(implicit context: app.Context): String = {
//    value
//      .replaceAll("#([0-9]+)", "[$0](%s/%s/%s/issue/$1)".format(context.path, repository.owner, repository.name))
//      .replaceAll("[0-9a-f]{40}", "[$0](%s/%s/%s/commit/$0)".format(context.path, repository.owner, repository.name))
//  }

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def toHtml(value: String, repository: service.RepositoryService.RepositoryInfo,
             wikiLink: Boolean)(implicit context: app.Context): String = {
    new PegDownProcessor(Extensions.AUTOLINKS | Extensions.WIKILINKS | Extensions.FENCED_CODE_BLOCKS | Extensions.TABLES)
      .markdownToHtml(value,
        new GitBucketLinkRender(wikiLink, context, repository),
        Map[String, VerbatimSerializer](VerbatimSerializer.DEFAULT -> new GitBucketVerbatimSerializer).asJava)
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
