package view

import util.StringUtil
import util.ControlUtil._
import util.Directory._
import org.parboiled.common.StringUtils
import org.pegdown._
import org.pegdown.ast._
import org.pegdown.LinkRenderer.Rendering
import java.text.Normalizer
import java.util.Locale
import scala.collection.JavaConverters._
import service.{ RequestCache, WikiService }
import org.asciidoctor.{ Asciidoctor, OptionsBuilder, SafeMode }

object Asciidoc {

  private[this] lazy val asciidoctor = Asciidoctor.Factory.create()

  /**
   * Converts Markdown of Wiki pages to HTML.
   */
  def toHtml(asciidoc: String, repository: service.RepositoryService.RepositoryInfo,
             enableWikiLink: Boolean, enableRefsLink: Boolean)(implicit context: app.Context): String = {
    val options = OptionsBuilder.options()
    options.safe(SafeMode.SECURE)
    asciidoctor.render(asciidoc, options)
  }
}

