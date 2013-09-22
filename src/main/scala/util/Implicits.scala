package util

import scala.util.matching.Regex
import javax.servlet.http.{HttpSession, HttpServletRequest}

/**
 * Provides some usable implicit conversions.
 */
object Implicits {

  implicit class RichSeq[A](seq: Seq[A]) {

    def splitWith(condition: (A, A) => Boolean): Seq[Seq[A]] = split(seq)(condition)

    @scala.annotation.tailrec
    private def split[A](list: Seq[A], result: Seq[Seq[A]] = Nil)(condition: (A, A) => Boolean): Seq[Seq[A]] =
      list match {
        case x :: xs => {
          xs.span(condition(x, _)) match {
            case (matched, remained) => split(remained, result :+ (x :: matched))(condition)
          }
        }
        case Nil => result
      }
  }

  implicit class RichString(value: String){
    def replaceBy(regex: Regex)(replace: Regex.MatchData => Option[String]): String = {
      val sb = new StringBuilder()
      var i = 0
      regex.findAllIn(value).matchData.foreach { m =>
        sb.append(value.substring(i, m.start))
        i = m.end
        replace(m) match {
          case Some(s) => sb.append(s)
          case None    => sb.append(m.matched)
        }
      }
      if(i < value.length){
        sb.append(value.substring(i))
      }
      sb.toString
    }
  }

  implicit class RichRequest(request: HttpServletRequest){

    def paths: Array[String] = request.getRequestURI.substring(request.getContextPath.length).split("/")

    def hasQueryString: Boolean = request.getQueryString != null

    def hasAttribute(name: String): Boolean = request.getAttribute(name) != null

  }

  implicit class RichSession(session: HttpSession){
    def putAndGet[T](key: String, value: T): T = {
      session.setAttribute(key, value)
      value
    }
  }

}