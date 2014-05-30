package view

import util.StringUtil
import util.ControlUtil._
import util.Directory._
import org.parboiled.common.StringUtils
import org.pegdown._
import org.pegdown.ast._
import org.pegdown.LinkRenderer.Rendering
import java.text.Normalizer
import java.util.Locale
import scala.collection.JavaConverters._
import service.{RequestCache, WikiService}

object Markdown {

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def toHtml(markdown: String, repository: service.RepositoryService.RepositoryInfo,
             enableWikiLink: Boolean, enableRefsLink: Boolean)(implicit context: app.Context): String = {
    // escape issue id
    val source = if(enableRefsLink){
      markdown.replaceAll("(?<=(\\W|^))#(\\d+)(?=(\\W|$))", "issue:$2")
    } else markdown

    val rootNode = new PegDownProcessor(
      Extensions.AUTOLINKS | Extensions.WIKILINKS | Extensions.FENCED_CODE_BLOCKS | Extensions.TABLES | Extensions.HARDWRAPS
    ).parseMarkdown(source.toCharArray)

    new GitBucketHtmlSerializer(markdown, repository, enableWikiLink, enableRefsLink).toHtml(rootNode)
  }
}

class GitBucketLinkRender(context: app.Context, repository: service.RepositoryService.RepositoryInfo,
                          enableWikiLink: Boolean) extends LinkRenderer with WikiService {
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

        val url = repository.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/wiki/" + StringUtil.urlEncode(page)

        if(getWikiPage(repository.owner, repository.name, page).isDefined){
          new Rendering(url, label)
        } else {
          new Rendering(url, label).withAttribute("class", "absent")
        }
      } catch {
        case e: java.io.UnsupportedEncodingException => throw new IllegalStateException
      }
    } else {
      super.render(node)
    }
  }
}

class GitBucketVerbatimSerializer extends VerbatimSerializer {
  def serialize(node: VerbatimNode, printer: Printer): Unit = {
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
    printer.print("<a target=\"_blank\" href=\"").print(fixUrl(url)).print("\">")
           .print("<img src=\"").print(fixUrl(url)).print("\"  alt=\"").printEncoded(printChildrenToString(imageNode)).print("\"/></a>")

  override protected def printLink(rendering: LinkRenderer.Rendering): Unit = {
    printer.print('<').print('a')
    printAttribute("href", fixUrl(rendering.href))
    for (attr <- rendering.attributes.asScala) {
      printAttribute(attr.name, attr.value)
    }
    printer.print('>').print(rendering.text).print("</a>")
  }

  private def fixUrl(url: String): String = {
    if(!enableWikiLink || url.startsWith("http://") || url.startsWith("https://") || url.startsWith("#")){
      url
    } else {
      repository.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/wiki/_blob/" + url
    }
  }

  private def printAttribute(name: String, value: String): Unit = {
    printer.print(' ').print(name).print('=').print('"').print(value).print('"')
  }

  private def printHeaderTag(node: HeaderNode): Unit = {
    val tag = s"h${node.getLevel}"
    val headerTextString = printChildrenToString(node)
    val anchorName = GitBucketHtmlSerializer.generateAnchorName(headerTextString)
    printer.print(s"""<$tag class="markdown-head">""")
    printer.print(s"""<a class="markdown-anchor-link" href="#$anchorName"></a>""")
    printer.print(s"""<a class="markdown-anchor" name="$anchorName"></a>""")
    visitChildren(node)
    printer.print(s"</$tag>")
  }

  override def visit(node: HeaderNode): Unit = {
    printHeaderTag(node)
  }

  override def visit(node: TextNode): Unit =  {
    // convert commit id and username to link.
    val text = if(enableRefsLink) convertRefsLinks(node.getText, repository, "issue:") else node.getText

    if (abbreviations.isEmpty) {
      printer.print(text)
    } else {
      printWithAbbreviations(text)
    }
  }
}

object GitBucketHtmlSerializer {

  private val Whitespace = "[\\s]".r

  def generateAnchorName(text: String): String = {
    val noWhitespace = Whitespace.replaceAllIn(text, "-")
    val normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD)
    val noSpecialChars = StringUtil.urlEncode(normalized)
    noSpecialChars.toLowerCase(Locale.ENGLISH)
  }
}
