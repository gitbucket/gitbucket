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
  
}