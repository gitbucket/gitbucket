package gitbucket.core.view

import java.text.Normalizer
import java.util
import java.util.regex.Pattern
import java.util.Locale

import gitbucket.core.controller.Context
import gitbucket.core.service.{RepositoryService, RequestCache}
import gitbucket.core.util.StringUtil
import io.github.gitbucket.markedj._
import io.github.gitbucket.markedj.Utils._
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.renderer.html.HtmlRenderer

object Markdown {

  /**
   * Converts Markdown of Wiki pages to HTML.
   *
   * @param repository the repository which contains the markdown
   * @param enableWikiLink if true then wiki style link is available in markdown
   * @param enableRefsLink if true then issue reference (e.g. #123) is rendered as link
   * @param enableAnchor if true then anchor for headline is generated
   * @param enableLineBreaks if true then render line breaks as &lt;br&gt;
   * @param enableTaskList if true then task list syntax is available
   * @param hasWritePermission true if user has writable to ths given repository
   * @param pages the list of existing Wiki pages
   */
  def toHtml(markdown: String,
             repository: RepositoryService.RepositoryInfo,
             enableWikiLink: Boolean,
             enableRefsLink: Boolean,
             enableAnchor: Boolean,
             enableLineBreaks: Boolean,
             enableTaskList: Boolean = false,
             hasWritePermission: Boolean = false,
             pages: List[String] = Nil)(implicit context: Context): String = {

    // escape task list
    val source = if(enableTaskList) escapeTaskList(markdown) else markdown

    val options = new Options()
    options.setSanitize(true)
    options.setBreaks(enableLineBreaks)

    val renderer = new GitBucketMarkedRenderer(options, repository,
      enableWikiLink, enableRefsLink, enableAnchor, enableTaskList, hasWritePermission, pages)

    val extensions = util.Arrays.asList(TablesExtension.create(), AutolinkExtension.create(), HeadingAnchorExtension.create());
    val parser = org.commonmark.parser.Parser.builder()
      .extensions(extensions)
      .build();
    val commonMarkRenderer = HtmlRenderer.builder()
      .extensions(extensions)
      .build();

    val parsed = parser.parse(source);
    var html = commonMarkRenderer.render(parsed);
    // helpers.decorateHtml(Marked.marked(source, options, renderer), repository)
    val transformer = new TextTransformer(repository, enableRefsLink, enableTaskList, hasWritePermission)

    helpers.decorateHtml(transformer.tranform(html), repository)
  }

  class TextTransformer (repository: RepositoryService.RepositoryInfo,
                         enableRefsLink: Boolean,
                         enableTaskList: Boolean,
                         hasWritePermission: Boolean
                        ) (implicit val context: Context) extends Object with LinkConverter with RequestCache {
    def tranform(text: String) : String = {
      val t1 = if (enableTaskList) convertCheckBox(text, hasWritePermission) else text
      val t2 = if (enableRefsLink) convertRefsLinks(t1, repository, "#", false) else t1
      t2
    }
    def convertCheckBox(text: String, hasWritePermission: Boolean): String = {
      val disabled = if (hasWritePermission) "" else "disabled"
      text.replaceAll("task:x:", """<input type="checkbox" class="task-list-item-checkbox" checked="checked" """ + disabled + "/>")
        .replaceAll("task: :", """<input type="checkbox" class="task-list-item-checkbox" """ + disabled + "/>")
    }
  }

  /**
   * Extends markedj Renderer for GitBucket
   */
  class GitBucketMarkedRenderer(options: Options,
                                repository: RepositoryService.RepositoryInfo,
                                enableWikiLink: Boolean,
                                enableRefsLink: Boolean,
                                enableAnchor: Boolean,
                                enableTaskList: Boolean,
                                hasWritePermission: Boolean,
                                pages: List[String])
                               (implicit val context: Context) extends Renderer(options) with LinkConverter with RequestCache {

    override  def heading(text: String, level: Int, raw: String): String = {
      val id = generateAnchorName(text)
      val out = new StringBuilder()

      out.append("<h" + level + " id=\"" + options.getHeaderPrefix + id + "\"")

      if(enableAnchor){
        out.append(" class=\"markdown-head\">")
        out.append("<a class=\"markdown-anchor-link\" href=\"#" + id + "\"><span class=\"octicon octicon-link\"></span></a>")
        out.append("<a class=\"markdown-anchor\" name=\"" + id + "\"></a>")
      } else {
        out.append(">")
      }

      out.append(text)
      out.append("</h" + level + ">\n")
      out.toString()
    }

    override def code(code: String, lang: String, escaped: Boolean): String = {
      "<pre class=\"prettyprint" + (if(lang != null) s" ${options.getLangPrefix}${lang}" else "" )+ "\">" +
        (if(escaped) code else escape(code, true)) + "</pre>"
    }

    override def list(body: String, ordered: Boolean): String = {
      var listType: String = null
      if (ordered) {
        listType = "ol"
      } else {
        listType = "ul"
      }
      if(body.contains("""class="task-list-item-checkbox"""")){
        "<" + listType + " class=\"task-list\">\n" + body + "</" + listType + ">\n"
      } else {
        "<" + listType + ">\n" + body + "</" + listType + ">\n"
      }
    }

    override def listitem(text: String): String = {
      if(text.contains("""class="task-list-item-checkbox" """)){
        "<li class=\"task-list-item\">" + text + "</li>\n"
      } else {
        "<li>" + text + "</li>\n"
      }
    }

    override def text(text: String): String = {
      // convert commit id and username to link.
      val t1 = if(enableRefsLink) convertRefsLinks(text, repository, "#", false) else text

      // convert task list to checkbox.
      val t2 = if(enableTaskList) convertCheckBox(t1, hasWritePermission) else t1

      t2
    }

    override def link(href: String, title: String, text: String): String = {
      super.link(fixUrl(href, false), title, text)
    }

    override def image(href: String, title: String, text: String): String = {
      super.image(fixUrl(href, true), title, text)
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
      lazy val urlWithRawParam: String = url + (if(isImage && !url.endsWith("?raw=true")) "?raw=true" else "")

      if(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/")){
        url
      } else if(url.startsWith("#")){
        ("#" + generateAnchorName(url.substring(1)))
      } else if(!enableWikiLink){
        if(context.currentPath.contains("/blob/")){
          urlWithRawParam
        } else if(context.currentPath.contains("/tree/")){
          val paths = context.currentPath.split("/")
          val branch = if(paths.length > 3) paths.drop(4).mkString("/") else repository.repository.defaultBranch
          repository.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/blob/" + branch + "/" + urlWithRawParam
        } else {
          val paths = context.currentPath.split("/")
          val branch = if(paths.length > 3) paths.last else repository.repository.defaultBranch
          repository.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/blob/" + branch + "/" + urlWithRawParam
        }
      } else {
        repository.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/wiki/_blob/" + url
      }
    }

  }

  def escapeTaskList(text: String): String = {
    Pattern.compile("""^( *)- \[([x| ])\] """, Pattern.MULTILINE).matcher(text).replaceAll("$1* task:$2: ")
  }

  def generateAnchorName(text: String): String = {
    val normalized = Normalizer.normalize(text.replaceAll("<.*>", "").replaceAll("[\\s]", "-"), Normalizer.Form.NFD)
    val encoded    = StringUtil.urlEncode(normalized)
    encoded.toLowerCase(Locale.ENGLISH)
  }

  def convertCheckBox(text: String, hasWritePermission: Boolean): String = {
    val disabled = if (hasWritePermission) "" else "disabled"
    text.replaceAll("task:x:", """<input type="checkbox" class="task-list-item-checkbox" checked="checked" """ + disabled + "/>")
      .replaceAll("task: :", """<input type="checkbox" class="task-list-item-checkbox" """ + disabled + "/>")
  }

}

