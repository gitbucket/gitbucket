package app

import model.Account
import org.scalatra._
import org.scalatra.json._
import org.json4s._
import jp.sf.amateras.scalatra.forms._
import service.AccountService

/**
 * Provides generic features for ScalatraServlet implementations.
 */
abstract class ControllerBase extends ScalatraFilter with ClientSideValidationFormSupport with JacksonJsonSupport {

  implicit val jsonFormats = DefaultFormats

  /**
   * Returns the context object for the request.
   */
  implicit def context: Context = Context(servletContext.getContextPath, LoginAccount)
  
  private def LoginAccount: Option[Account] = {
    session.get("LOGIN_ACCOUNT") match {
      case Some(x: Account) => Some(x)
      case _ => None
    }
  }

  /**
   * Allows only the repository owner and administrators.
   */
  protected def ownerOnly(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) if(x.userType == AccountService.Administrator) => action
        case Some(x) if(request.getRequestURI.split("/")(1) == x.userName) => action
        case _ => redirect("/signin")
      }
    }
  }

  /**
   * Allows only the repository owner and administrators.
   */
  protected def ownerOnly[T](action: T => Any) = {
    (form: T) => {
      context.loginAccount match {
        case Some(x) if(x.userType == AccountService.Administrator) => action(form)
        case Some(x) if(request.getRequestURI.split("/")(1) == x.userName) => action(form)
        case _ => redirect("/signin")
      }
    }
  }

  /**
   * Allows only signed in users.
   */
  protected def usersOnly(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) => action
        case None    => redirect("/signin")
      }
    }
  }

  /**
   * Allows only signed in users.
   */
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