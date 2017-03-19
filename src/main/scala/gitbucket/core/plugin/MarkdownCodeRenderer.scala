package gitbucket.core.plugin

import io.github.gitbucket.markedj.Options
import io.github.gitbucket.markedj.Utils._

/**
 * A render engine to render code in a makrdown to String.
 */
trait MarkdownCodeRenderer {

  /**
   * Render the given code to String.
   */
  def renderCode(code: String, lang: String, escaped: Boolean, options: Options): String
}

object DefaultMarkdownCodeRenderer extends MarkdownCodeRenderer {
  override def renderCode(code: String, lang: String, escaped: Boolean, options: Options): String = {
    "<pre class=\"prettyprint" + (if(lang != null) s" ${options.getLangPrefix}${lang}" else "" )+ "\">" +
    (if(escaped) code else escape(code, true)) + "</pre>"
  }
}
