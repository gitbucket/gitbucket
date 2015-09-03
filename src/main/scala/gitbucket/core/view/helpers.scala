package gitbucket.core.view

import java.text.SimpleDateFormat
import java.util.{Date, Locale, TimeZone}

import gitbucket.core.controller.Context
import gitbucket.core.model.CommitState
import gitbucket.core.plugin.{RenderRequest, PluginRegistry}
import gitbucket.core.service.{RepositoryService, RequestCache}
import gitbucket.core.util.{FileUtil, JGitUtil, StringUtil}

import play.twirl.api.Html

/**
 * Provides helper methods for Twirl templates.
 */
object helpers extends AvatarImageProvider with LinkConverter with RequestCache {

  /**
   * Format java.util.Date to "yyyy-MM-dd HH:mm:ss".
   */
  def datetime(date: Date): String = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)

  val timeUnits = List(
    (1000L, "second"),
    (1000L * 60, "minute"),
    (1000L * 60 * 60, "hour"),
    (1000L * 60 * 60 * 24, "day"),
    (1000L * 60 * 60 * 24 * 30, "month"),
    (1000L * 60 * 60 * 24 * 365, "year")
  ).reverse

  /**
   * Format java.util.Date to "x {seconds/minutes/hours/days/months/years} ago"
   */
  def datetimeAgo(date: Date): String = {
    val duration = new Date().getTime - date.getTime
    timeUnits.find(tuple => duration / tuple._1 > 0) match {
      case Some((unitValue, unitString)) =>
        val value = duration / unitValue
        s"${value} ${unitString}${if (value > 1) "s" else ""} ago"
      case None => "just now"
    }
  }

  /**
   * Format java.util.Date to "x {seconds/minutes/hours/days} ago"
   * If duration over 1 month, format to "d MMM (yyyy)"
   */
  def datetimeAgoRecentOnly(date: Date): String = {
    val duration = new Date().getTime - date.getTime
    timeUnits.find(tuple => duration / tuple._1 > 0) match {
      case Some((_, "month")) => s"on ${new SimpleDateFormat("d MMM", Locale.ENGLISH).format(date)}"
      case Some((_, "year")) => s"on ${new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(date)}"
      case Some((unitValue, unitString)) =>
        val value = duration / unitValue
        s"${value} ${unitString}${if (value > 1) "s" else ""} ago"
      case None => "just now"
    }
  }


  /**
   * Format java.util.Date to "yyyy-MM-dd'T'hh:mm:ss'Z'".
   */
  def datetimeRFC3339(date: Date): String = {
    val sf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    sf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sf.format(date)
  }

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

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def markdown(value: String,
               repository: RepositoryService.RepositoryInfo,
               enableWikiLink: Boolean,
               enableRefsLink: Boolean,
               enableTaskList: Boolean = false,
               hasWritePermission: Boolean = false,
               pages: List[String] = Nil)(implicit context: Context): Html =
    Html(Markdown.toHtml(value, repository, enableWikiLink, enableRefsLink, true, enableTaskList, hasWritePermission, pages))

  def renderMarkup(filePath: List[String], fileContent: String, branch: String,
                   repository: RepositoryService.RepositoryInfo,
                   enableWikiLink: Boolean, enableRefsLink: Boolean, enableAnchor: Boolean)(implicit context: Context): Html = {

    val fileName  = filePath.reverse.head.toLowerCase
    val extension = FileUtil.getExtension(fileName)
    val renderer  = PluginRegistry().getRenderer(extension)
    renderer.render(RenderRequest(filePath, fileContent, branch, repository, enableWikiLink, enableRefsLink, enableAnchor, context))
  }

  def isRenderable(fileName: String): Boolean = {
    PluginRegistry().renderableExtensions.exists(extension => fileName.toLowerCase.endsWith("." + extension))
  }

  /**
   * Returns &lt;img&gt; which displays the avatar icon for the given user name.
   * This method looks up Gravatar if avatar icon has not been configured in user settings.
   */
  def avatar(userName: String, size: Int, tooltip: Boolean = false, mailAddress: String = "")(implicit context: Context): Html =
    getAvatarImageHtml(userName, size, mailAddress, tooltip)

  /**
   * Returns &lt;img&gt; which displays the avatar icon for the given mail address.
   * This method looks up Gravatar if avatar icon has not been configured in user settings.
   */
  def avatar(commit: JGitUtil.CommitInfo, size: Int)(implicit context: Context): Html =
    getAvatarImageHtml(commit.authorName, size, commit.authorEmailAddress)

  /**
   * Converts commit id, issue id and username to the link.
   */
  def link(value: String, repository: RepositoryService.RepositoryInfo)(implicit context: Context): Html =
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
      pattern.r.replaceAllIn(s, (m: Match) => replacer(m).replace("$", "\\$"))
    }
  }

  /**
   * Convert link notations in the activity message.
   */
  def activityMessage(message: String)(implicit context: Context): Html =
    Html(message
      .replaceAll("\\[issue:([^\\s]+?)/([^\\s]+?)#((\\d+))\\]"     , s"""<a href="${context.path}/$$1/$$2/issues/$$3">$$1/$$2#$$3</a>""")
      .replaceAll("\\[pullreq:([^\\s]+?)/([^\\s]+?)#((\\d+))\\]"   , s"""<a href="${context.path}/$$1/$$2/pull/$$3">$$1/$$2#$$3</a>""")
      .replaceAll("\\[repo:([^\\s]+?)/([^\\s]+?)\\]"               , s"""<a href="${context.path}/$$1/$$2\">$$1/$$2</a>""")
      .replaceAll("\\[branch:([^\\s]+?)/([^\\s]+?)#([^\\s]+?)\\]"  , (m: Match) => s"""<a href="${context.path}/${m.group(1)}/${m.group(2)}/tree/${encodeRefName(m.group(3))}">${m.group(3)}</a>""")
      .replaceAll("\\[tag:([^\\s]+?)/([^\\s]+?)#([^\\s]+?)\\]"     , (m: Match) => s"""<a href="${context.path}/${m.group(1)}/${m.group(2)}/tree/${encodeRefName(m.group(3))}">${m.group(3)}</a>""")
      .replaceAll("\\[user:([^\\s]+?)\\]"                          , (m: Match) => user(m.group(1)).body)
      .replaceAll("\\[commit:([^\\s]+?)/([^\\s]+?)\\@([^\\s]+?)\\]", (m: Match) => s"""<a href="${context.path}/${m.group(1)}/${m.group(2)}/commit/${m.group(3)}">${m.group(1)}/${m.group(2)}@${m.group(3).substring(0, 7)}</a>""")
    )

  /**
   * Remove html tags from the given Html instance.
   */
  def removeHtml(html: Html): Html = Html(html.body.replaceAll("<.+?>", ""))

  /**
   * URL encode except '/'.
   */
  def encodeRefName(value: String): String = StringUtil.urlEncode(value).replace("%2F", "/")

  def urlEncode(value: String): String = StringUtil.urlEncode(value)

  def urlEncode(value: Option[String]): String = value.map(urlEncode).getOrElse("")

  /**
   * Generates the url to the repository.
   */
  def url(repository: RepositoryService.RepositoryInfo)(implicit context: Context): String =
    s"${context.path}/${repository.owner}/${repository.name}"

  /**
   * Generates the url to the account page.
   */
  def url(userName: String)(implicit context: Context): String = s"${context.path}/${userName}"

  /**
   * Returns the url to the root of assets.
   */
  def assets(implicit context: Context): String = s"${context.path}/assets"

  /**
   * Generates the text link to the account page.
   * If user does not exist or disabled, this method returns user name as text without link.
   */
  def user(userName: String, mailAddress: String = "", styleClass: String = "")(implicit context: Context): Html =
    userWithContent(userName, mailAddress, styleClass)(Html(userName))

  /**
   * Generates the avatar link to the account page.
   * If user does not exist or disabled, this method returns avatar image without link.
   */
  def avatarLink(userName: String, size: Int, mailAddress: String = "", tooltip: Boolean = false)(implicit context: Context): Html =
    userWithContent(userName, mailAddress)(avatar(userName, size, tooltip, mailAddress))

  private def userWithContent(userName: String, mailAddress: String = "", styleClass: String = "")(content: Html)(implicit context: Context): Html =
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

  def pre(value: Html): Html = Html(s"<pre>${value.body.trim.split("\n").map(_.trim).mkString("\n")}</pre>")

  /**
   * Implicit conversion to add mkHtml() to Seq[Html].
   */
  implicit class RichHtmlSeq(seq: Seq[Html]) {
    def mkHtml(separator: String) = Html(seq.mkString(separator))
    def mkHtml(separator: scala.xml.Elem) = Html(seq.mkString(separator.toString))
  }

  def commitStateIcon(state: CommitState) = Html(state match {
    case CommitState.PENDING => "●"
    case CommitState.SUCCESS => "&#x2714;"
    case CommitState.ERROR   => "×"
    case CommitState.FAILURE => "×"
  })

  def commitStateText(state: CommitState, commitId:String) = state match {
    case CommitState.PENDING => "Waiting to hear about "+commitId.substring(0,8)
    case CommitState.SUCCESS => "All is well"
    case CommitState.ERROR   => "Failed"
    case CommitState.FAILURE => "Failed"
  }
}
