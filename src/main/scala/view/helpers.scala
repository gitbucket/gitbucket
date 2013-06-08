package view
import java.util.Date
import java.text.SimpleDateFormat
import twirl.api.Html

object helpers {
  
  /**
   * Format java.util.Date to "yyyy/MM/dd HH:mm:ss".
   */
  def datetime(date: Date): String = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(date)
  
  /**
   * Format java.util.Date to "yyyy/MM/dd".
   */
  def date(date: Date): String = new SimpleDateFormat("yyyy/MM/dd").format(date)
  
  // TODO escape html tags using HtmlEscapeUtils (Commons Lang)
  def format(value: String): Html = Html(
    value.replaceAll(" ", "&nbsp;").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;").replaceAll("\n", "<br>"))
    
  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def markdown(value: String, repository: service.RepositoryService.RepositoryInfo,
               enableWikiLink: Boolean, enableCommitLink: Boolean, enableIssueLink: Boolean)(implicit context: app.Context): Html = {
    Html(Markdown.toHtml(value, repository, enableWikiLink, enableCommitLink, enableIssueLink))
  }
  
  /**
   * Cut the given string by specified length.
   */
  def cut(message: String, length: Int): String = {
    if(message.length > length){
      message.substring(0, length) + "..."
    } else {
      message
    }
  }
  
}