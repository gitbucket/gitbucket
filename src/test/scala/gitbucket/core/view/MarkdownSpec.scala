package gitbucket.core.view

import org.scalatest.FunSpec

class MarkdownSpec extends FunSpec {

  import Markdown._

  describe("generateAnchorName") {
    it("should convert whitespace characters to hyphens") {
      val before = "foo bar baz"
      val after = generateAnchorName(before)
      assert(after == "foo-bar-baz")
    }

    it("should normalize characters with diacritics") {
      val before = "Dónde estará mi vida"
      val after = generateAnchorName(before)
      assert(after == "do%cc%81nde-estara%cc%81-mi-vida")
    }

    it("should omit special characters") {
      val before = "foo!bar@baz>9000"
      val after = generateAnchorName(before)
      assert(after == "foo%21bar%40baz%3e9000")
    }
  }

  describe("escapeTaskList") {
    it("should convert '- [ ] ' to '* task: :'") {
      val before = "- [ ] aaaa"
      val after = escapeTaskList(before)
      assert(after == "* task: : aaaa")
    }

    it("should convert '  - [ ] ' to '  * task: :'") {
      val before = "  - [ ]   aaaa"
      val after = escapeTaskList(before)
      assert(after == "  * task: :   aaaa")
    }

    it("should convert only first '- [ ] '") {
      val before = "   - [ ]   aaaa - [ ] bbb"
      val after = escapeTaskList(before)
      assert(after == "   * task: :   aaaa - [ ] bbb")
    }

    it("should convert '- [x] ' to '* task:x:'") {
      val before = "  - [x]   aaaa"
      val after = escapeTaskList(before)
      assert(after == "  * task:x:   aaaa")
    }

    it("should convert multi lines") {
      val before = """
tasks
- [x] aaaa
- [ ] bbb
"""
      val after = escapeTaskList(before)
      assert(after == """
tasks
* task:x: aaaa
* task: : bbb
""")
    }

    it("should not convert if inserted before '- [ ] '") {
      val before = " a  - [ ]   aaaa"
      val after = escapeTaskList(before)
      assert(after == " a  - [ ]   aaaa")
    }

    it("should not convert '- [] '") {
      val before = "  - []   aaaa"
      val after = escapeTaskList(before)
      assert(after == "  - []   aaaa")
    }

    it("should not convert '- [ ]a'") {
      val before = "  - [ ]a aaaa"
      val after = escapeTaskList(before)
      assert(after == "  - [ ]a aaaa")
    }

    it("should not convert '-[ ] '") {
      val before = "  -[ ]   aaaa"
      val after = escapeTaskList(before)
      assert(after == "  -[ ]   aaaa")
    }
  }
}
