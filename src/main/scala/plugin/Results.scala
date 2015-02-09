package plugin

import play.twirl.api.Html

object Results {
  case class Redirect(path: String)
  case class Fragment(html: Html)
}
