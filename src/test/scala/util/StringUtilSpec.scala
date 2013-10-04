package util

import org.specs2.mutable._

class StringUtilSpec extends Specification {

  "urlDecode" should {
    "decode encoded string to original string" in {
      val encoded = StringUtil.urlEncode("あいうえお")
      StringUtil.urlDecode(encoded) mustEqual "あいうえお"
    }
  }

  "splitWords" should {
    "split string by whitespaces" in {
      val split = StringUtil.splitWords("aa bb\tcc　dd \t　ee")
      split mustEqual Array("aa", "bb", "cc", "dd", "ee")
    }
  }

  "escapeHtml" should {
    "escape &, <, > and \"" in {
      StringUtil.escapeHtml("<a href=\"/test\">a & b</a>") mustEqual "&lt;a href=&quot;/test&quot;&gt;a &amp; b&lt;/a&gt;"
    }
  }

  "md5" should {
    "generate MD5 hash" in {
      StringUtil.md5("abc") mustEqual "900150983cd24fb0d6963f7d28e17f72"
    }
  }

  "sha1" should {
    "generate SHA1 hash" in {
      StringUtil.sha1("abc") mustEqual "a9993e364706816aba3e25717850c26c9cd0d89d"
    }
  }
}
