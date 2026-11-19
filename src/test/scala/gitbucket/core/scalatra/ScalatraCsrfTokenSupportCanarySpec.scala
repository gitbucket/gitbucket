package gitbucket.core.scalatra

import org.scalatra.ScalatraServlet
import org.scalatra.test.scalatest.ScalatraFunSuite

/**
 * Fails when Scalatra's own `org.scalatra.CsrfTokenSupport` stops creating a session on every request. Then the
 * backport [[gitbucket.core.scalatra.CsrfTokenSupport]] may no longer be needed.
 */
class ScalatraCsrfTokenSupportCanarySpec extends ScalatraFunSuite {

  class ScalatraCsrfServlet extends ScalatraServlet with org.scalatra.CsrfTokenSupport {
    get("/") { "ok" }
  }

  addServlet(new ScalatraCsrfServlet, "/*")

  test("Scalatra's CsrfTokenSupport still creates a session on every request") {
    get("/") {
      assert(
        Option(header("Set-Cookie")).exists(_.startsWith("JSESSIONID=")),
        "Scalatra's CsrfTokenSupport no longer creates a session on every request: check whether the backport " +
          "gitbucket.core.scalatra.CsrfTokenSupport can be removed (see its Scaladoc)"
      )
    }
  }
}
