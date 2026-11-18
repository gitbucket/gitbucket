package gitbucket.core.service

import org.scalatest.funspec.AnyFunSpec

class RepositorySearchServiceSpec extends AnyFunSpec {

  describe("getHighlightText") {
    it("should escape the content and highlight the keyword") {
      val (text, lineNumber) = RepositorySearchService.getHighlightText("line 1\n<b>foo</b> bar", "foo")
      assert(text == "&lt;b&gt;<span class=\"highlight\">foo</span>&lt;/b&gt; bar")
      assert(lineNumber == 2)
    }
    it("should escape the first lines if the content doesn't contain the keyword") {
      val (text, lineNumber) = RepositorySearchService.getHighlightText("<img src=x onerror=alert(1)>\nline 2", "foo")
      assert(text == "&lt;img src=x onerror=alert(1)&gt;\nline 2")
      assert(lineNumber == 1)
    }
  }
}
