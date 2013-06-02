package app

import model.Account
import org.scalatra._
import org.scalatra.json._
import org.json4s._
import jp.sf.amateras.scalatra.forms._

/**
 * Provides generic features for ScalatraServlet implementations.
 */
abstract class ControllerBase extends ScalatraFilter with ClientSideValidationFormSupport with JacksonJsonSupport {
  
  implicit val jsonFormats = DefaultFormats
  
  implicit def context: Context = Context(servletContext.getContextPath, LoginAccount)
  
  private def LoginAccount: Option[Account] = {
    session.get("LOGIN_ACCOUNT") match {
      case Some(x: Account) => Some(x)
      case _ => None
    }
  }

  protected def usersOnly(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) => action
        case None    => redirect("/signin")
      }
    }
  }

  protected def usersOnly[T](action: T => Any) = {
    (form: T) => {
      context.loginAccount match {
        case Some(x) => action(form)
        case None    => redirect("/signin")
      }
    }
  }

//  protected def adminOnly()

}

case class Context(path: String, loginAccount: Option[Account])