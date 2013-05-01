package view
import java.util.Date
import java.text.SimpleDateFormat

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
  def format(value: String): twirl.api.Html = twirl.api.Html(
    value.replaceAll(" ", "&nbsp;").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;").replaceAll("\n", "<br>"))

  def markdown(value: String): twirl.api.Html = {
    import org.pegdown._
    val html = new PegDownProcessor().markdownToHtml(value)
    twirl.api.Html(html)
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