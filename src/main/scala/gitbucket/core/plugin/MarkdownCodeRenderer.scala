package gitbucket.core.plugin

import io.github.gitbucket.markedj.Options

/**
 * A render engine to render code in a makrdown to String.
 */
trait MarkdownCodeRenderer {

  /**
   * Render the given code to String.
   */
  def renderCode(code: String, lang: String, escaped: Boolean, options: Options): String
}
