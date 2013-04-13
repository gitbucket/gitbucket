package app

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
  
  protected def withValidation(validator: Map[String, String] => ValidationResult, params: Map[String, String])(action: => Any): Any = {
    validator(params).valid match {
      case true  => action
      case false => throw new RuntimeException("Invalid Request") // TODO show error page?
    }
  }
  
  case class ValidationResult(valid: Boolean, errors: Map[String, String]){
    def toJSON(): JObject = {
      JObject(
        "valid"  -> JBool(valid),
        "errors" -> JObject(errors.map { case (key, value) =>
          JField(key, JString(value))
        }.toList)
      )
    }
  }

}