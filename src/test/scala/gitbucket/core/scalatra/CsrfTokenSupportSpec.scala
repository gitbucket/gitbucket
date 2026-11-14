package gitbucket.core.scalatra

import org.scalatra.ScalatraServlet
import org.scalatra.test.scalatest.ScalatraFunSuite

/**
 * Tests of the backport [[CsrfTokenSupport]], with plain Scalatra only (no GitBucket code). The same cases as
 * `CsrfTokenSpec` in Scalatra PR #2085.
 */
class CsrfTokenSupportSpec extends ScalatraFunSuite {

  class TestServlet extends ScalatraServlet with CsrfTokenSupport {
    get("/") { "ok" }
    get("/form") { csrfToken }
    get("/session") { session.setAttribute("x", "y"); "session" }
    get("/reset") { CsrfTokenSupport.resetToken(request); "reset" }
    post("/signin") { resetCsrfToken(); csrfToken }
    post("/") { "posted" }
  }

  class EagerServlet extends ScalatraServlet with CsrfTokenSupport {
    override protected def csrfEagerToken: Boolean = true
    get("/") { "ok" }
  }

  class AuthorizationServlet extends ScalatraServlet with CsrfTokenSupport {
    override protected def csrfCredentialHeaders: Seq[String] = CsrfTokenSupport.CredentialHeaders :+ "Authorization"
    post("/") { "posted" }
  }

  addServlet(new EagerServlet, "/eager/*")
  addServlet(new AuthorizationServlet, "/authorization/*")
  addServlet(new TestServlet, "/*")

  private val Key = CsrfTokenSupport.DefaultKey

  private def setsSession: Boolean = Option(header("Set-Cookie")).exists(_.startsWith("JSESSIONID="))

  test("a GET that doesn't read the token creates no session") {
    get("/") {
      assert(status == 200)
      assert(!setsSession, String.valueOf(header("Set-Cookie")))
    }
  }

  test("a POST without a cookie is not checked and creates no session") {
    post("/") {
      assert(status == 200)
      assert(!setsSession, String.valueOf(header("Set-Cookie")))
    }
  }

  test("reading the token creates the session and the token; a POST with the cookie needs it") {
    session {
      val token = get("/form") {
        assert(setsSession)
        body
      }
      assert(token.nonEmpty)
      post("/") { assert(status == 403) }
      post("/", Key -> "wrong") { assert(status == 403) }
      post("/", Key -> "") { assert(status == 403) }
      post("/", Key -> token) { assert(status == 200) }
      post("/", headers = Map("X-CSRF-TOKEN" -> token)) { assert(status == 200) }
    }
  }

  test("a POST with a cookie but without a session is refused") {
    post("/", Seq(Key -> "guessed"), Map("Cookie" -> "other=value")) { assert(status == 403) }
    post("/", Seq(Key -> "guessed"), Map("Cookie" -> "JSESSIONID=expired")) { assert(status == 403) }
  }

  test("a session without a token refuses unsafe requests") {
    session {
      get("/session") { assert(setsSession) }
      post("/") { assert(status == 403) }
    }
  }

  test("resetCsrfToken and resetToken replace the token") {
    session {
      val firstToken = get("/form") { body }
      val secondToken = post("/signin", Key -> firstToken) {
        assert(status == 200)
        body
      }
      assert(secondToken != firstToken)
      post("/", Key -> firstToken) { assert(status == 403) }
      post("/", Key -> secondToken) { assert(status == 200) }

      get("/reset") { assert(status == 200) }
      val thirdToken = get("/form") { body }
      assert(thirdToken != secondToken)
      post("/", Key -> secondToken) { assert(status == 403) }
      post("/", Key -> thirdToken) { assert(status == 200) }
    }
  }

  test("csrfEagerToken creates the session and the token on every request") {
    get("/eager/") {
      assert(status == 200)
      assert(setsSession)
    }
  }

  test("an Authorization header makes a POST subject to the check if configured") {
    post("/authorization/", Seq.empty, Map("Authorization" -> "Basic dXNlcjpwYXNz")) { assert(status == 403) }
    post("/authorization/") { assert(status == 200) }
  }
}
