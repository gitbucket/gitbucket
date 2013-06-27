package view
import java.util.Date
import java.text.SimpleDateFormat
import twirl.api.Html

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
   * Converts Markdown of Wiki pages to HTML.
   */
  def markdown(value: String, repository: service.RepositoryService.RepositoryInfo,
               enableWikiLink: Boolean, enableCommitLink: Boolean, enableIssueLink: Boolean)(implicit context: app.Context): Html = {
    Html(Markdown.toHtml(value, repository, enableWikiLink, enableCommitLink, enableIssueLink))
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

}
