package app

import util.Validations._

import org.scalatra._
import org.scalatra.json._
import org.json4s._
import org.json4s.jackson._

/**
 * Provides generic features for ScalatraServlet implementations.
 */
abstract class ServletBase extends ScalatraServlet with JacksonJsonSupport {
  
  implicit val jsonFormats = DefaultFormats
  
  // TODO get from session
  val LoginUser = System.getProperty("user.name")
  
  def get[T](path: String, form: MappingValueType[T])(action: T => Any) = {
    super.get(path){
      withValidation(form, params){ obj: T =>
        action(obj)
      }
    }
    registerValidate(path, form)
  }

  def post[T](path: String, form: MappingValueType[T])(action: T => Any) = {
    super.post(path){
      withValidation(form, params){ obj: T =>
        action(obj)
      }
    }
    registerValidate(path, form)
  }
  
  private def registerValidate[T](path: String, form: MappingValueType[T]) = {
    super.post(path.replaceFirst("/$", "") + "/validate"){
      contentType = "application/json"
      form.validateAsJSON(params)
    }
  }
  
}