package view
import java.util.Date
import java.text.SimpleDateFormat

import twirl.api.Html

import org.pegdown._
import org.pegdown.LinkRenderer.Rendering
import org.pegdown.ast.WikiLinkNode

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
  def markdown(value: String, repository: service.RepositoryService.RepositoryInfo, wikiLink: Boolean)(implicit context: app.Context): twirl.api.Html = {
    Html(Markdown.toHtml(value, repository, wikiLink))
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