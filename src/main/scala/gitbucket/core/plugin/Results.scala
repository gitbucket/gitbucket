package gitbucket.core.plugin

import play.twirl.api.Html

/**
 * Defines result case classes returned by plugin controller.
 */
object Results {
  case class Redirect(path: String)
  case class Fragment(html: Html)
}
