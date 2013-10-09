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
  def markdown(value: String, repository: service.RepositoryService.RepositoryInfo,
               enableWikiLink: Boolean, enableRefsLink: Boolean)(implicit context: app.Context): Html =
    Html(Markdown.toHtml(value, repository, enableWikiLink, enableRefsLink))

  /**
   * Returns &lt;img&gt; which displays the avatar icon.
   * Looks up Gravatar if avatar icon has not been configured in user settings.
   */
  def avatar(userName: String, size: Int, tooltip: Boolean = false)(implicit context: app.Context): Html =
    getAvatarImageHtml(userName, size, "", tooltip)

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

  def activityMessage(message: String)(implicit context: app.Context): Html =
    Html(message
      .replaceAll("\\[issue:([^\\s]+?)/([^\\s]+?)#((\\d+))\\]"   , s"""<a href="${context.path}/$$1/$$2/issues/$$3">$$1/$$2#$$3</a>""")
      .replaceAll("\\[pullreq:([^\\s]+?)/([^\\s]+?)#((\\d+))\\]" , s"""<a href="${context.path}/$$1/$$2/pull/$$3">$$1/$$2#$$3</a>""")
      .replaceAll("\\[repo:([^\\s]+?)/([^\\s]+?)\\]"             , s"""<a href="${context.path}/$$1/$$2\">$$1/$$2</a>""")
      .replaceAll("\\[branch:([^\\s]+?)/([^\\s]+?)#([^\\s]+?)\\]", s"""<a href="${context.path}/$$1/$$2/tree/$$3">$$3</a>""")
      .replaceAll("\\[tag:([^\\s]+?)/([^\\s]+?)#([^\\s]+?)\\]"   , s"""<a href="${context.path}/$$1/$$2/tree/$$3">$$3</a>""")
      .replaceAll("\\[user:([^\\s]+?)\\]"                        , s"""<a href="${context.path}/$$1">$$1</a>""")
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

  def isPast(date: Date): Boolean = System.currentTimeMillis > date.getTime

  /**
   * Implicit conversion to add mkHtml() to Seq[Html].
   */
  implicit class RichHtmlSeq(seq: Seq[Html]) {
    def mkHtml(separator: String) = Html(seq.mkString(separator))
    def mkHtml(separator: scala.xml.Elem) = Html(seq.mkString(separator.toString))
  }

}
