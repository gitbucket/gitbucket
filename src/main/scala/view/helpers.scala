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
   * Converts the issue number and the commit id to the link.
   */
  private def markdownFilter(value: String, repository: service.RepositoryService.RepositoryInfo)(implicit context: app.Context): String = {
    value
      .replaceAll("#([0-9]+)", "[$0](%s/%s/%s/issue/$1)".format(context.path, repository.owner, repository.name))
      .replaceAll("[0-9a-f]{40}", "[$0](%s/%s/%s/commit/$0)".format(context.path, repository.owner, repository.name))
  }
    
  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def markdown(value: String, repository: service.RepositoryService.RepositoryInfo, wikiLink: Boolean)(implicit context: app.Context): twirl.api.Html = {
    import org.pegdown._
    val html = new PegDownProcessor(Extensions.AUTOLINKS|Extensions.WIKILINKS|Extensions.FENCED_CODE_BLOCKS)
      .markdownToHtml(markdownFilter(value, repository), new LinkRenderer(){
        override def render(node: WikiLinkNode): Rendering = {
          if(wikiLink){
            super.render(node)
          } else {
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
        }      
      })

    Html(html)
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