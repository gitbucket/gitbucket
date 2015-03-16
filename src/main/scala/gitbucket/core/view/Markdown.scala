package gitbucket.core.view

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

import gitbucket.core.controller.Context
import gitbucket.core.service.{RepositoryService, RequestCache, WikiService}
import gitbucket.core.util.StringUtil
import org.parboiled.common.StringUtils
import org.pegdown.LinkRenderer.Rendering
import org.pegdown._
import org.pegdown.ast._

import scala.collection.JavaConverters._

object Markdown {

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def toHtml(markdown: String,
             repository: RepositoryService.RepositoryInfo,
             enableWikiLink: Boolean,
             enableRefsLink: Boolean,
             enableTaskList: Boolean = false,
             hasWritePermission: Boolean = false,
             pages: List[String] = Nil)(implicit context: Context): String = {

    // escape issue id
    val s = if(enableRefsLink){
      markdown.replaceAll("(?<=(\\W|^))#(\\d+)(?=(\\W|$))", "issue:$2")
    } else markdown

    // escape task list
    val source = if(enableTaskList){
      GitBucketHtmlSerializer.escapeTaskList(s)
    } else s

    val rootNode = new PegDownProcessor(
      Extensions.AUTOLINKS | Extensions.WIKILINKS | Extensions.FENCED_CODE_BLOCKS | Extensions.TABLES | Extensions.HARDWRAPS | Extensions.SUPPRESS_ALL_HTML
    ).parseMarkdown(source.toCharArray)

    new GitBucketHtmlSerializer(markdown, repository, enableWikiLink, enableRefsLink, enableTaskList, hasWritePermission, pages).toHtml(rootNode)
  }
}

class GitBucketLinkRender(
    context: Context,
    repository: RepositoryService.RepositoryInfo,
    enableWikiLink: Boolean,
    pages: List[String]) extends LinkRenderer with WikiService {

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

        if(pages.contains(page)){
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
    repository: RepositoryService.RepositoryInfo,
    enableWikiLink: Boolean,
    enableRefsLink: Boolean,
    enableTaskList: Boolean,
    hasWritePermission: Boolean,
    pages: List[String]
  )(implicit val context: Context) extends ToHtmlSerializer(
    new GitBucketLinkRender(context, repository, enableWikiLink, pages),
    Map[String, VerbatimSerializer](VerbatimSerializer.DEFAULT -> new GitBucketVerbatimSerializer).asJava
  ) with LinkConverter with RequestCache {

  override protected def printImageTag(imageNode: SuperNode, url: String): Unit = {
    printer.print("<a target=\"_blank\" href=\"").print(fixUrl(url, true)).print("\">")
           .print("<img src=\"").print(fixUrl(url, true)).print("\"  alt=\"").printEncoded(printChildrenToString(imageNode)).print("\"/></a>")
  }

  override protected def printLink(rendering: LinkRenderer.Rendering): Unit = {
    printer.print('<').print('a')
    printAttribute("href", fixUrl(rendering.href))
    for (attr <- rendering.attributes.asScala) {
      printAttribute(attr.name, attr.value)
    }
    printer.print('>').print(rendering.text).print("</a>")
  }

  private def fixUrl(url: String, isImage: Boolean = false): String = {
    if(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("#") || url.startsWith("/")){
      url
    } else if(!enableWikiLink){
      if(context.currentPath.contains("/blob/")){
        url + (if(isImage) "?raw=true" else "")
      } else if(context.currentPath.contains("/tree/")){
        val paths = context.currentPath.split("/")
        val branch = if(paths.length > 3) paths.drop(4).mkString("/") else repository.repository.defaultBranch
        repository.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/blob/" + branch + "/" + url + (if(isImage) "?raw=true" else "")
      } else {
        val paths = context.currentPath.split("/")
        val branch = if(paths.length > 3) paths.last else repository.repository.defaultBranch
        repository.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/blob/" + branch + "/" + url + (if(isImage) "?raw=true" else "")
      }
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
    val t = if(enableRefsLink) convertRefsLinks(node.getText, repository, "issue:") else node.getText

    // convert task list to checkbox.
    val text = if(enableTaskList) GitBucketHtmlSerializer.convertCheckBox(t, hasWritePermission) else t

    if (abbreviations.isEmpty) {
      printer.print(text)
    } else {
      printWithAbbreviations(text)
    }
  }

  override def visit(node: BulletListNode): Unit = {
    if (printChildrenToString(node).contains("""class="task-list-item-checkbox" """)) {
      printer.println().print("""<ul class="task-list">""").indent(+2)
      visitChildren(node)
      printer.indent(-2).println().print("</ul>")
    } else {
      printIndentedTag(node, "ul")
    }
  }

  override def visit(node: ListItemNode): Unit = {
    if (printChildrenToString(node).contains("""class="task-list-item-checkbox" """)) {
      printer.println()
      printer.print("""<li class="task-list-item">""")
      visitChildren(node)
      printer.print("</li>")
    } else {
      printer.println()
      printTag(node, "li")
    }
  }

  override def visit(node: ExpLinkNode) {
    printLink(linkRenderer.render(node, printLinkChildrenToString(node)))
  }

  def printLinkChildrenToString(node: SuperNode) = {
    val priorPrinter = printer
    printer = new Printer()
    visitLinkChildren(node)
    val result = printer.getString()
    printer = priorPrinter
    result
  }

  def visitLinkChildren(node: SuperNode) {
    import scala.collection.JavaConversions._
    node.getChildren.foreach(child => child match {
      case node: ExpImageNode => visitLinkChild(node)
      case node: SuperNode => visitLinkChildren(node)
      case _ => child.accept(this)
    })
  }

  def visitLinkChild(node: ExpImageNode) {
    printer.print("<img src=\"").print(fixUrl(node.url, true)).print("\"  alt=\"").printEncoded(printChildrenToString(node)).print("\"/>")
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

  def escapeTaskList(text: String): String = {
    Pattern.compile("""^( *)- \[([x| ])\] """, Pattern.MULTILINE).matcher(text).replaceAll("$1* task:$2: ")
  }

  def convertCheckBox(text: String, hasWritePermission: Boolean): String = {
    val disabled = if (hasWritePermission) "" else "disabled"
    text.replaceAll("task:x:", """<input type="checkbox" class="task-list-item-checkbox" checked="checked" """ + disabled + "/>")
        .replaceAll("task: :", """<input type="checkbox" class="task-list-item-checkbox" """ + disabled + "/>")
  }
}
