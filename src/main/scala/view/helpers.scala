package view
import java.util.Date
import java.text.SimpleDateFormat
import twirl.api.Html
import util.StringUtil
import service.RequestCache

/**
 * Provides helper methods for Twirl templates.
 */
object helpers extends AvatarImageProvider with LinkConverter with RequestCache {

  /**
   * Format java.util.Date to "yyyy-MM-dd HH:mm:ss".
   */
  def datetime(date: Date): String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)

  /**
   * Format java.util.Date to "yyyy-MM-dd'T'hh:mm:ss'Z'".
   */
  def datetimeRFC3339(date: Date): String = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'").format(date).replaceAll("(\\d\\d)(\\d\\d)$","$1:$2")

  /**
   * Format java.util.Date to "yyyy-MM-dd".
   */
  def date(date: Date): String = new SimpleDateFormat("yyyy-MM-dd").format(date)

  /**
   * Returns singular if count is 1, otherwise plural.
   * If plural is not specified, returns singular + "s" as plural.
   */
  def plural(count: Int, singular: String, plural: String = ""): String =
    if(count == 1) singular else if(plural.isEmpty) singular + "s" else plural

  private[this] val renderersBySuffix: Seq[(String, (List[String], String, String, service.RepositoryService.RepositoryInfo, Boolean, Boolean, app.Context) => Html)] =
    Seq(
      ".md" -> ((filePath, fileContent, branch, repository, enableWikiLink, enableRefsLink, context) => markdown(fileContent, repository, enableWikiLink, enableRefsLink)(context)),
      ".markdown" -> ((filePath, fileContent, branch, repository, enableWikiLink, enableRefsLink, context) => markdown(fileContent, repository, enableWikiLink, enableRefsLink)(context))
    )

  def renderableSuffixes: Seq[String] = renderersBySuffix.map(_._1)

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def markdown(value: String, repository: service.RepositoryService.RepositoryInfo,
               enableWikiLink: Boolean, enableRefsLink: Boolean)(implicit context: app.Context): Html =
    Html(Markdown.toHtml(value, repository, enableWikiLink, enableRefsLink))

  def renderMarkup(filePath: List[String], fileContent: String, branch: String,
                   repository: service.RepositoryService.RepositoryInfo,
                   enableWikiLink: Boolean, enableRefsLink: Boolean)(implicit context: app.Context): Html = {

    val fileNameLower = filePath.reverse.head.toLowerCase
    renderersBySuffix.find { case (suffix, _) => fileNameLower.endsWith(suffix) } match {
      case Some((_, handler)) => handler(filePath, fileContent, branch, repository, enableWikiLink, enableRefsLink, context)
      case None => Html(
        s"<tt>${
          fileContent.split("(\\r\\n)|\\n").map(xml.Utility.escape(_)).mkString("<br/>")
        }</tt>"
      )
    }
  }

  /**
   * Returns &lt;img&gt; which displays the avatar icon for the given user name.
   * This method looks up Gravatar if avatar icon has not been configured in user settings.
   */
  def avatar(userName: String, size: Int, tooltip: Boolean = false)(implicit context: app.Context): Html =
    getAvatarImageHtml(userName, size, "", tooltip)

  /**
   * Returns &lt;img&gt; which displays the avatar icon for the given mail address.
   * This method looks up Gravatar if avatar icon has not been configured in user settings.
   */
  def avatar(commit: util.JGitUtil.CommitInfo, size: Int)(implicit context: app.Context): Html =
    getAvatarImageHtml(commit.committer, size, commit.mailAddress)

  /**
   * Converts commit id, issue id and username to the link.
   */
  def link(value: String, repository: service.RepositoryService.RepositoryInfo)(implicit context: app.Context): Html =
    Html(convertRefsLinks(value, repository))

  def cut(value: String, length: Int): String =
    if(value.length > length){
      value.substring(0, length) + "..."
    } else {
      value
    }

  import scala.util.matching.Regex._
  implicit class RegexReplaceString(s: String) {
    def replaceAll(pattern: String, replacer: (Match) => String): String = {
      pattern.r.replaceAllIn(s, replacer)
    }
  }

  /**
   * Convert link notations in the activity message.
   */
  def activityMessage(message: String)(implicit context: app.Context): Html =
    Html(message
      .replaceAll("\\[issue:([^\\s]+?)/([^\\s]+?)#((\\d+))\\]"   , s"""<a href="${context.path}/$$1/$$2/issues/$$3">$$1/$$2#$$3</a>""")
      .replaceAll("\\[pullreq:([^\\s]+?)/([^\\s]+?)#((\\d+))\\]" , s"""<a href="${context.path}/$$1/$$2/pull/$$3">$$1/$$2#$$3</a>""")
      .replaceAll("\\[repo:([^\\s]+?)/([^\\s]+?)\\]"             , s"""<a href="${context.path}/$$1/$$2\">$$1/$$2</a>""")
      .replaceAll("\\[branch:([^\\s]+?)/([^\\s]+?)#([^\\s]+?)\\]", (m: Match) => s"""<a href="${context.path}/${m.group(1)}/${m.group(2)}/tree/${encodeRefName(m.group(3))}">${m.group(3)}</a>""")
      .replaceAll("\\[tag:([^\\s]+?)/([^\\s]+?)#([^\\s]+?)\\]"   , (m: Match) => s"""<a href="${context.path}/${m.group(1)}/${m.group(2)}/tree/${encodeRefName(m.group(3))}">${m.group(3)}</a>""")
      .replaceAll("\\[user:([^\\s]+?)\\]"                        , (m: Match) => user(m.group(1)).body)
    )

  /**
   * URL encode except '/'.
   */
  def encodeRefName(value: String): String = StringUtil.urlEncode(value).replace("%2F", "/")

  def urlEncode(value: String): String = StringUtil.urlEncode(value)

  def urlEncode(value: Option[String]): String = value.map(urlEncode).getOrElse("")

  /**
   * Generates the url to the repository.
   */
  def url(repository: service.RepositoryService.RepositoryInfo)(implicit context: app.Context): String =
    s"${context.path}/${repository.owner}/${repository.name}"

  /**
   * Generates the url to the account page.
   */
  def url(userName: String)(implicit context: app.Context): String = s"${context.path}/${userName}"

  /**
   * Returns the url to the root of assets.
   */
  def assets(implicit context: app.Context): String = s"${context.path}/assets"

  /**
   * Generates the text link to the account page.
   * If user does not exist or disabled, this method returns user name as text without link.
   */
  def user(userName: String, mailAddress: String = "", styleClass: String = "")(implicit context: app.Context): Html =
    userWithContent(userName, mailAddress, styleClass)(Html(userName))

  /**
   * Generates the avatar link to the account page.
   * If user does not exist or disabled, this method returns avatar image without link.
   */
  def avatarLink(userName: String, size: Int, mailAddress: String = "", tooltip: Boolean = false)(implicit context: app.Context): Html =
    userWithContent(userName, mailAddress)(avatar(userName, size, tooltip))

  private def userWithContent(userName: String, mailAddress: String = "", styleClass: String = "")(content: Html)(implicit context: app.Context): Html =
    (if(mailAddress.isEmpty){
      getAccountByUserName(userName)
    } else {
      getAccountByMailAddress(mailAddress)
    }).map { account =>
      Html(s"""<a href="${url(account.userName)}" class="${styleClass}">${content}</a>""")
    } getOrElse content


  /**
   * Test whether the given Date is past date.
   */
  def isPast(date: Date): Boolean = System.currentTimeMillis > date.getTime

  /**
   * Returns file type for AceEditor.
   */
  def editorType(fileName: String): String = {
    fileName.toLowerCase match {
      case x if(x.endsWith(".bat"))     => "batchfile"
      case x if(x.endsWith(".java"))    => "java"
      case x if(x.endsWith(".scala"))   => "scala"
      case x if(x.endsWith(".js"))      => "javascript"
      case x if(x.endsWith(".css"))     => "css"
      case x if(x.endsWith(".md"))      => "markdown"
      case x if(x.endsWith(".html"))    => "html"
      case x if(x.endsWith(".xml"))     => "xml"
      case x if(x.endsWith(".c"))       => "c_cpp"
      case x if(x.endsWith(".cpp"))     => "c_cpp"
      case x if(x.endsWith(".coffee"))  => "coffee"
      case x if(x.endsWith(".ejs"))     => "ejs"
      case x if(x.endsWith(".hs"))      => "haskell"
      case x if(x.endsWith(".json"))    => "json"
      case x if(x.endsWith(".jsp"))     => "jsp"
      case x if(x.endsWith(".jsx"))     => "jsx"
      case x if(x.endsWith(".cl"))      => "lisp"
      case x if(x.endsWith(".clojure")) => "lisp"
      case x if(x.endsWith(".lua"))     => "lua"
      case x if(x.endsWith(".php"))     => "php"
      case x if(x.endsWith(".py"))      => "python"
      case x if(x.endsWith(".rdoc"))    => "rdoc"
      case x if(x.endsWith(".rhtml"))   => "rhtml"
      case x if(x.endsWith(".ruby"))    => "ruby"
      case x if(x.endsWith(".sh"))      => "sh"
      case x if(x.endsWith(".sql"))     => "sql"
      case x if(x.endsWith(".tcl"))     => "tcl"
      case x if(x.endsWith(".vbs"))     => "vbscript"
      case x if(x.endsWith(".yml"))     => "yaml"
      case _ => "plain_text"
    }
  }

  /**
   * Implicit conversion to add mkHtml() to Seq[Html].
   */
  implicit class RichHtmlSeq(seq: Seq[Html]) {
    def mkHtml(separator: String) = Html(seq.mkString(separator))
    def mkHtml(separator: scala.xml.Elem) = Html(seq.mkString(separator.toString))
  }

}
