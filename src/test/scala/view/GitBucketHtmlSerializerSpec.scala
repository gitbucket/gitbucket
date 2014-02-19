package view

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
}
