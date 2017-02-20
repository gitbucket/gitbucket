package gitbucket.core.view

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService.RepositoryInfo
import org.scalatest.FunSpec
import org.scalatest.mockito.MockitoSugar

class HelpersSpec extends FunSpec with MockitoSugar {

  private implicit val context = mock[Context]
  private val repository = mock[RepositoryInfo]

  import helpers._

  describe("detect and render links") {

    it("should pass identical string when no link is present") {
      val before = "Description"
      val after = detectAndRenderLinks(before, repository)
      assert(after == before)
    }

    it("should convert a single link") {
      val before = "http://example.com"
      val after = detectAndRenderLinks(before, repository)
      assert(after == """<a href="http://example.com">http://example.com</a>""")
    }

    it("should convert a single link within trailing text") {
      val before = "Example Project. http://example.com"
      val after = detectAndRenderLinks(before, repository)
      assert(after == """Example Project. <a href="http://example.com">http://example.com</a>""")
    }

    it("should convert a mulitple links within text") {
      val before = "Example Project. http://example.com. (See also https://github.com/)"
      val after = detectAndRenderLinks(before, repository)
      assert(after == """Example Project. <a href="http://example.com">http://example.com</a>. (See also <a href="https://github.com/">https://github.com/</a>)""")
    }

    it("should properly escape html metacharacters") {
      val before = "<>&"
      val after = detectAndRenderLinks(before, repository)
      assert(after == """&lt;&gt;&amp;""")
    }

    it("should escape html metacharacters adjacent to a link") {
      val before = "<http://example.com>"
      val after = detectAndRenderLinks(before, repository)
      assert(after == """&lt;<a href="http://example.com">http://example.com</a>&gt;""")
    }

    it("should stop link recognition at a metacharacter") {
      val before = "http://exa<mple.com"
      val after = detectAndRenderLinks(before, repository)
      assert(after == """<a href="http://exa">http://exa</a>&lt;mple.com""")
    }

    it("should make sure there are no double quotes in the href attribute") {
      val before = "http://exa\"mple.com"
      val after = detectAndRenderLinks(before, repository)
      assert(after == """<a href="http://exa&quot;mple.com">http://exa"mple.com</a>""")
    }
  }
}
