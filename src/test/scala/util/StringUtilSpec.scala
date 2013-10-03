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

}
