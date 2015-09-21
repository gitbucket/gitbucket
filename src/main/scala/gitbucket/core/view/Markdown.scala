package gitbucket.core.view

import java.text.Normalizer
import java.util.{Optional, Locale}
import java.util.regex.Pattern

import gitbucket.core.controller.Context
import gitbucket.core.service.{RepositoryService, RequestCache, WikiService}
import gitbucket.core.util.StringUtil
import io.github.gitbucket.markedj._
import io.github.gitbucket.markedj.Utils._

object Markdown {

  /**
   * Converts Markdown of Wiki pages to HTML.
   *
   * @param repository the repository which contains the markdown
   * @param enableWikiLink if true then wiki style link is available in markdown
   * @param enableRefsLink if true then issue reference (e.g. #123) is rendered as link
   * @param enableAnchor if true then anchor for headline is generated
   * @param enableTaskList if true then task list syntax is available
   * @param hasWritePermission
   * @param pages the list of existing Wiki pages
   */
  def toHtml(markdown: String,
             repository: RepositoryService.RepositoryInfo,
             enableWikiLink: Boolean,
             enableRefsLink: Boolean,
             enableAnchor: Boolean,
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

    val options = new Options()
    val renderer = new GitBucketMarkedRenderer(options, repository, enableWikiLink, enableRefsLink, enableTaskList, hasWritePermission, pages)
    Marked.marked(source, options, renderer)
  }
}

class GitBucketMarkedRenderer(options: Options, repository: RepositoryService.RepositoryInfo,
                              enableWikiLink: Boolean, enableRefsLink: Boolean, enableTaskList: Boolean, hasWritePermission: Boolean,
                              pages: List[String])
                             (implicit val context: Context) extends Renderer(options) with LinkConverter with RequestCache {

  override def code(code: String, lang: Optional[String], escaped: Boolean): String = {
    "<pre class=\"prettyprint" + (if(lang.isPresent) s" ${options.getLangPrefix}${lang.get}" else "" )+ "\">" +
      (if(escaped) code else escape(code, true)) + "</pre>"
  }

  override def list(body: String, ordered: Boolean): String = {
    var listType: String = null
    if (ordered) {
      listType = "ol"
    }
    else {
      listType = "ul"
    }
    if(body.contains("""class="task-list-item-checkbox"""")){
      return "<" + listType + " class=\"task-list\">\n" + body + "</" + listType + ">\n"
    } else {
      return "<" + listType + ">\n" + body + "</" + listType + ">\n"
    }
  }

  override def listitem(text: String): String = {
    if(text.contains("""class="task-list-item-checkbox" """)){
      return "<li class=\"task-list-item\">" + text + "</li>"
    } else {
      return "<li>" + text + "</li>"
    }
  }

  override def text(text: String): String = {
    // convert commit id and username to link.
    val t1 = if(enableRefsLink) convertRefsLinks(text, repository, "issue:") else text

    // convert task list to checkbox.
    val t2 = if(enableTaskList) GitBucketHtmlSerializer.convertCheckBox(t1, hasWritePermission) else t1

    t2
  }

  override def image(href: String, title: Optional[String], text: String): String = {
    super.image(fixUrl(href, true), title, text);
  }

  override def nolink(text: String): String = {
    if(enableWikiLink && text.startsWith("[[") && text.endsWith("]]")){
      val link = text.replaceAll("(^\\[\\[|\\]\\]$)", "")

      val (label, page) = if(link.contains('|')){
        val i = link.indexOf('|')
        (link.substring(0, i), link.substring(i + 1))
      } else {
        (link, link)
      }

      val url = repository.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/wiki/" + StringUtil.urlEncode(page)
      if(pages.contains(page)){
        "<a href=\"" + url + "\">" + escape(label) + "</a>"
      } else {
        "<a href=\"" + url + "\" class=\"absent\">" + escape(label) + "</a>"
      }
    } else {
      escape(text)
    }
  }

  private def fixUrl(url: String, isImage: Boolean = false): String = {
    if(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/")){
      url
    } else if(url.startsWith("#")){
      ("#" + GitBucketHtmlSerializer.generateAnchorName(url.substring(1)))
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

}

//class GitBucketHtmlSerializer(
//    markdown: String,
//    repository: RepositoryService.RepositoryInfo,
//    enableWikiLink: Boolean,
//    enableRefsLink: Boolean,
//    enableAnchor: Boolean,
//    enableTaskList: Boolean,
//    hasWritePermission: Boolean,
//    pages: List[String]
//  )(implicit val context: Context) extends ToHtmlSerializer(
//    new GitBucketLinkRender(context, repository, enableWikiLink, pages),
//    Map[String, VerbatimSerializer](VerbatimSerializer.DEFAULT -> new GitBucketVerbatimSerializer).asJava
//  ) with LinkConverter with RequestCache {
//
//  override protected def printLink(rendering: LinkRenderer.Rendering): Unit = {
//    printer.print('<').print('a')
//    printAttribute("href", fixUrl(rendering.href))
//    for (attr <- rendering.attributes.asScala) {
//      printAttribute(attr.name, attr.value)
//    }
//    printer.print('>').print(rendering.text).print("</a>")
//  }
//
//  private def printHeaderTag(node: HeaderNode): Unit = {
//    val tag = s"h${node.getLevel}"
//    val child = node.getChildren.asScala.headOption
//    val anchorName = child match {
//      case Some(x: AnchorLinkNode) => x.getName
//      case Some(x: TextNode)       => x.getText
//      case _ => GitBucketHtmlSerializer.generateAnchorName(extractText(node)) // TODO
//    }
//
//    printer.print(s"""<$tag class="markdown-head">""")
//    if(enableAnchor){
//      printer.print(s"""<a class="markdown-anchor-link" href="#$anchorName"></a>""")
//      printer.print(s"""<a class="markdown-anchor" name="$anchorName"></a>""")
//    }
//    child match {
//      case Some(x: AnchorLinkNode) => printer.print(x.getText)
//      case _ => visitChildren(node)
//    }
//    printer.print(s"</$tag>")
//  }
//
//  override def visit(node: VerbatimNode) {
//    val printer = new Printer()
//    val serializer = verbatimSerializers.get(VerbatimSerializer.DEFAULT)
//    serializer.serialize(node, printer)
//    val html = printer.getString
//
//    // convert commit id and username to link.
//    val t = if(enableRefsLink) convertRefsLinks(html, repository, "issue:", escapeHtml = false) else html
//
//    this.printer.print(t)
//  }
//}

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
