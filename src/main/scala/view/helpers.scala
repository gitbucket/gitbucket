package view
import java.util.Date
import java.text.SimpleDateFormat
import twirl.api.Html
import util.StringUtil
import service.AccountService

/**
 * Provides helper methods for Twirl templates.
 */
object helpers {
  
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
               enableWikiLink: Boolean, enableCommitLink: Boolean, enableIssueLink: Boolean)(implicit context: app.Context): Html = {
    Html(Markdown.toHtml(value, repository, enableWikiLink, enableCommitLink, enableIssueLink))
  }

  def activityMessage(message: String)(implicit context: app.Context): Html = {
    val a = s"a $message aa $$1 a"
    
    Html(message
      .replaceAll("\\[issue:([^\\s]+?)/([^\\s]+?)#((\\d+))\\]"   , s"""<a href="${context.path}/$$1/$$2/issues/$$3">$$1/$$2#$$3</a>""")
      .replaceAll("\\[repo:([^\\s]+?)/([^\\s]+?)\\]"             , s"""<a href="${context.path}/$$1/$$2\">$$1/$$2</a>""")
      .replaceAll("\\[branch:([^\\s]+?)/([^\\s]+?)#([^\\s]+?)\\]", s"""<a href="${context.path}/$$1/$$2/tree/$$3">$$3</a>""")
      .replaceAll("\\[tag:([^\\s]+?)/([^\\s]+?)#([^\\s]+?)\\]"   , s"""<a href="${context.path}/$$1/$$2/tree/$$3">$$3</a>""")
      .replaceAll("\\[user:([^\\s]+?)\\]"                        , s"""<a href="${context.path}/$$1">$$1</a>""")
    )
  }

  /**
   * Generates the url to the repository.
   */
  def url(repository: service.RepositoryService.RepositoryInfo)(implicit context: app.Context): String =
    "%s/%s/%s".format(context.path, repository.owner, repository.name)

  /**
   * Generates the url to the account page.
   */
  def url(userName: String)(implicit context: app.Context): String = "%s/%s".format(context.path, userName)

  /**
   * Returns the url to the root of assets.
   */
  def assets(implicit context: app.Context): String = "%s/assets".format(context.path)

  /**
   * Converts issue id and commit id to link.
   */
  def link(value: String, repository: service.RepositoryService.RepositoryInfo)(implicit context: app.Context): Html =
    Html(value
      // escape HTML tags
      .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;")
      // convert issue id to link
      .replaceAll("(^|\\W)#(\\d+)(\\W|$)", "$1<a href=\"%s/%s/%s/issues/$2\">#$2</a>$3".format(context.path, repository.owner, repository.name))
      // convert commit id to link
      .replaceAll("(^|\\W)([a-f0-9]{40})(\\W|$)", "$1<a href=\"%s/%s/%s/commit/$2\">$2</a>$3").format(context.path, repository.owner, repository.name))

  /**
   * Returns &lt;img&gt; which displays the avatar icon.
   * Looks up Gravatar if avatar icon has not been configured in user settings.
   */
  def avatar(userName: String, size: Int, tooltip: Boolean = false)(implicit context: app.Context): Html = {
    val account = Option(context.request.getAttribute("cache.account." + userName).asInstanceOf[model.Account]).orElse {
      new AccountService {}.getAccountByUserName(userName).map { account =>
        context.request.setAttribute("cache.account." + userName, account)
        account
      }
    }
    val src = account.collect { case account if(account.image.isEmpty) =>
      s"""http://www.gravatar.com/avatar/${StringUtil.md5(account.mailAddress)}?s=${size}"""
    } getOrElse {
      s"""${context.path}/${userName}/_avatar"""
    }
    if(tooltip){
      Html(s"""<img src=${src} class="avatar" style="width: ${size}px; height: ${size}:px" data-toggle="tooltip" title=${userName}/>""")
    } else {
      Html(s"""<img src=${src} class="avatar" style="width: ${size}px; height: ${size}:px" />""")
    }
  }

  /**
   * Implicit conversion to add mkHtml() to Seq[Html].
   */
  implicit def extendsHtmlSeq(seq: Seq[Html]) = new {
    def mkHtml(separator: String) = Html(seq.mkString(separator))
    def mkHtml(separator: scala.xml.Elem) = Html(seq.mkString(separator.toString))
  }

}
