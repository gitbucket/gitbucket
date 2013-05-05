package view
import java.util.Date
import java.text.SimpleDateFormat

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
  def format(value: String): twirl.api.Html = twirl.api.Html(
    value.replaceAll(" ", "&nbsp;").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;").replaceAll("\n", "<br>"))

  /**
   * Converts Markdown of Wiki pages to HTML. This method supports Wiki links.
   */
  def markdown(value: String, repository: app.RepositoryInfo)(implicit context: app.Context): twirl.api.Html = {
    import org.pegdown._
    val html = new PegDownProcessor(Extensions.AUTOLINKS|Extensions.WIKILINKS|Extensions.FENCED_CODE_BLOCKS)
      .markdownToHtml(value, new LinkRenderer(){
        override def render(node: WikiLinkNode): Rendering = {
          try {
            val text = node.getText
            val (label, page) = if(text.contains('|')){
              val i = text.indexOf('|')
              (text.substring(0, i), text.substring(i + 1))
            } else {
              (text, text)
            }
            val url = "%s/%s/%s/wiki/%s".format(context.path, repository.owner, repository.name, 
              java.net.URLEncoder.encode(page.replace(' ', '-'), "UTF-8"))
            new Rendering(url, label);
          } catch {
            case e: java.io.UnsupportedEncodingException => throw new IllegalStateException();
          }
        }      
      })
    twirl.api.Html(html)
  }
  
  /**
   * Converts Markdown to HTML. This method does not support Wiki links.
   */
  def markdown(value: String): twirl.api.Html = {
    val html = new PegDownProcessor(Extensions.FENCED_CODE_BLOCKS).markdownToHtml(value, new LinkRenderer())
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