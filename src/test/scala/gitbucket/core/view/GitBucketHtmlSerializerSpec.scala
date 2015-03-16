package gitbucket.core.view

import org.specs2.mutable._

class GitBucketHtmlSerializerSpec extends Specification {

  import GitBucketHtmlSerializer._

  "generateAnchorName" should {
    "convert whitespace characters to hyphens" in {
      val before = "foo bar baz"
      val after = generateAnchorName(before)
      after mustEqual "foo-bar-baz"
    }

    "normalize characters with diacritics" in {
      val before = "Dónde estará mi vida"
      val after = generateAnchorName(before)
      after mustEqual "do%cc%81nde-estara%cc%81-mi-vida"
    }

    "omit special characters" in {
      val before = "foo!bar@baz>9000"
      val after = generateAnchorName(before)
      after mustEqual "foo%21bar%40baz%3e9000"
    }
  }

  "escapeTaskList" should {
    "convert '- [ ] ' to '* task: :'" in {
      val before = "- [ ] aaaa"
      val after = escapeTaskList(before)
      after mustEqual "* task: : aaaa"
    }

    "convert '  - [ ] ' to '  * task: :'" in {
      val before = "  - [ ]   aaaa"
      val after = escapeTaskList(before)
      after mustEqual "  * task: :   aaaa"
    }

    "convert only first '- [ ] '" in {
      val before = "   - [ ]   aaaa - [ ] bbb"
      val after = escapeTaskList(before)
      after mustEqual "   * task: :   aaaa - [ ] bbb"
    }

    "convert '- [x] ' to '* task:x:'" in {
      val before = "  - [x]   aaaa"
      val after = escapeTaskList(before)
      after mustEqual "  * task:x:   aaaa"
    }

    "convert multi lines" in {
      val before = """
tasks
- [x] aaaa
- [ ] bbb
"""
      val after = escapeTaskList(before)
      after mustEqual """
tasks
* task:x: aaaa
* task: : bbb
"""
    }

    "no convert if inserted before '- [ ] '" in {
      val before = " a  - [ ]   aaaa"
      val after = escapeTaskList(before)
      after mustEqual " a  - [ ]   aaaa"
    }

    "no convert '- [] '" in {
      val before = "  - []   aaaa"
      val after = escapeTaskList(before)
      after mustEqual "  - []   aaaa"
    }

    "no convert '- [ ]a'" in {
      val before = "  - [ ]a aaaa"
      val after = escapeTaskList(before)
      after mustEqual "  - [ ]a aaaa"
    }

    "no convert '-[ ] '" in {
      val before = "  -[ ]   aaaa"
      val after = escapeTaskList(before)
      after mustEqual "  -[ ]   aaaa"
    }
  }
}

