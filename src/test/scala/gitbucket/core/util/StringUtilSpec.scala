package gitbucket.core.util

//import org.specs2.mutable._
import org.scalatest.FunSpec

class StringUtilSpec extends FunSpec {

  describe("urlEncode") {
    it("should encode whitespace to %20") {
      val encoded = StringUtil.urlEncode("aa bb")
      assert(encoded == "aa%20bb")
    }
  }

  describe("urlDecode") {
    it("should decode encoded string to original string") {
      val encoded = StringUtil.urlEncode("あいうえお")
      assert(StringUtil.urlDecode(encoded) == "あいうえお")
    }
    it("should decode en%20 to whitespace") {
      assert(StringUtil.urlDecode("aa%20bb") == "aa bb")
    }
  }

  describe("splitWords") {
    it("should split string by whitespaces") {
      val split = StringUtil.splitWords("aa bb\tcc　dd \t　ee")
      assert(split === Array("aa", "bb", "cc", "dd", "ee"))
    }
  }

  describe("escapeHtml") {
    it("should escape &, <, > and \"") {
      assert(StringUtil.escapeHtml("<a href=\"/test\">a & b</a>") == "&lt;a href=&quot;/test&quot;&gt;a &amp; b&lt;/a&gt;")
    }
  }

  describe("md5") {
    it("should generate MD5 hash") {
      assert(StringUtil.md5("abc") == "900150983cd24fb0d6963f7d28e17f72")
    }
  }

  describe("sha1") {
    it("should generate SHA1 hash") {
      assert(StringUtil.sha1("abc") == "a9993e364706816aba3e25717850c26c9cd0d89d")
    }
  }

  describe("extractIssueId") {
    it("should extract '#xxx' and return extracted id") {
      assert(StringUtil.extractIssueId("(refs #123)").toSeq == Seq("123"))
    }
    it("should return Nil from message which does not contain #xxx") {
      assert(StringUtil.extractIssueId("this is test!").toSeq == Nil)
    }
  }

  describe("extractCloseId") {
    it("should extract 'close #xxx' and return extracted id") {
      assert(StringUtil.extractCloseId("(close #123)").toSeq == Seq("123"))
    }
    it("should returns Nil from message which does not contain close command") {
      assert(StringUtil.extractCloseId("(refs #123)").toSeq == Nil)
    }
  }
}
