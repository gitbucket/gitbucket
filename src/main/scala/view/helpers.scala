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

}

/**
 * Provides implicit conversions for Twirl templates.
 */
object implicits {
  
  implicit def extendsHtmlSeq(seq: Seq[Html]) = new {
    def mkHtml(separator: String) = Html(seq.mkString(separator))
    def mkHtml(separator: scala.xml.Elem) = Html(seq.mkString(separator.toString))
  }
  
}