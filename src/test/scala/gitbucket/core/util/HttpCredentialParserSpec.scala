package gitbucket.core.util

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

import gitbucket.core.model.BasicAuthCredentials
import gitbucket.core.util.HttpCredentialParser.parseBasicAuth
import org.scalatest.funspec.AnyFunSpec

class HttpCredentialParserSpec extends AnyFunSpec {
  describe("parseBasicAuth") {
    it("is empty when no auth type") {
      assert(parseBasicAuth("") === None)
    }

    it("is empty for other auth type") {
      assert(parseBasicAuth("Foo Bar") === None)
    }

    it("is empty when basic auth missing credentials") {
      assert(parseBasicAuth("Basic") === None)
      assert(parseBasicAuth("Basic ") === None)
    }

    it("parses user name without password") {
      val encoded = new String(Base64.getEncoder.encode("qux".getBytes(UTF_8)), UTF_8)

      assert(Some(BasicAuthCredentials("qux", "")) === parseBasicAuth("Basic " + encoded))
    }

    it("parses user name with password") {
      val encoded = new String(Base64.getEncoder.encode("foo:bar".getBytes(UTF_8)), UTF_8)

      assert(Some(BasicAuthCredentials("foo", "bar")) === parseBasicAuth("Basic " + encoded))
    }
  }
}
