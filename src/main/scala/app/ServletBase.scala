package app

/**
 * Provides generic features for ScalatraServlet implementations.
 */
trait ServletBase {
  
  // TODO get from session
  val LoginUser = System.getProperty("user.name")

}