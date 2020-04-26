package gitbucket.core.service

import scala.util.Try

object PaginationHelper {

  def page(page: Option[String]) = {

    page
      .flatMap(pageStr => Try(pageStr.toInt).toOption)
      .map(Math.max(1, _)) // remove negative pages
      .getOrElse(1)
  }

}
