package gitbucket.core.view

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService.RepositoryInfo
import org.scalatest.funspec.AnyFunSpec
import org.mockito.Mockito._

class MarkdownSpec extends AnyFunSpec {

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

  describe("toHtml") {
    it("should fix url at the repository root") {
      val repository = mock(classOf[RepositoryInfo])
      val context = mock(classOf[Context])
      when(context.currentPath).thenReturn("/user/repo")
      when(repository.httpUrl(context)).thenReturn("http://localhost:8080/git/user/repo.git")

      val html = Markdown.toHtml(
        markdown = "[ChangeLog](CHANGELOG.md)",
        repository = repository,
        branch = "master",
        enableWikiLink = false,
        enableRefsLink = true,
        enableAnchor = true,
        enableLineBreaks = true
      )(context)

      assert(
        html == """<p><a href="http://localhost:8080/user/repo/blob/master/CHANGELOG.md">ChangeLog</a></p>"""
      )
    }

    it("should fix sub directory url at the file list") {
      val repository = mock(classOf[RepositoryInfo])
      val context = mock(classOf[Context])
      when(context.currentPath).thenReturn("/user/repo/tree/master/sub/dir")
      when(repository.httpUrl(context)).thenReturn("http://localhost:8080/git/user/repo.git")

      val html = Markdown.toHtml(
        markdown = "[ChangeLog](CHANGELOG.md)",
        repository = repository,
        branch = "master",
        enableWikiLink = false,
        enableRefsLink = true,
        enableAnchor = true,
        enableLineBreaks = true
      )(context)

      assert(
        html == """<p><a href="http://localhost:8080/user/repo/blob/master/sub/dir/CHANGELOG.md">ChangeLog</a></p>"""
      )
    }

    it("should fix sub directory url at the blob view") {
      val repository = mock(classOf[RepositoryInfo])
      val context = mock(classOf[Context])
      when(context.currentPath).thenReturn("/user/repo/blob/master/sub/dir/README.md")
      when(repository.httpUrl(context)).thenReturn("http://localhost:8080/git/user/repo.git")

      val html = Markdown.toHtml(
        markdown = "[ChangeLog](CHANGELOG.md)",
        repository = repository,
        branch = "master",
        enableWikiLink = false,
        enableRefsLink = true,
        enableAnchor = true,
        enableLineBreaks = true
      )(context)

      assert(
        html == """<p><a href="http://localhost:8080/user/repo/blob/master/sub/dir/CHANGELOG.md">ChangeLog</a></p>"""
      )
    }
  }
}
