package gitbucket.core.util

import org.scalatra.i18n.Messages
import org.scalatest.funspec.AnyFunSpec

class ValidationsSpec extends AnyFunSpec with Validations {

  describe("identifier") {
    it("should validate id string ") {
      assert(identifier.validate("id", "aa_ZZ-00.01", null) == None)
      assert(identifier.validate("id", "_aaaa", null) == Some("id starts with invalid character."))
      assert(identifier.validate("id", "-aaaa", null) == Some("id starts with invalid character."))
      assert(identifier.validate("id", "aa_ZZ#01", null) == Some("id contains invalid character."))
    }
  }

  describe("color") {
    it("should validate color string ") {
      val messages = Messages()
      assert(color.validate("color", "#88aaff", messages) == None)
      assert(color.validate("color", "#gghhii", messages) == Some("color must be '#[0-9a-fA-F]{6}'."))
    }
  }

  describe("date") {
//    "validate date string " in {
//      date().validate("date", "2013-10-05", Map[String, String]()) mustEqual Nil
//      date().validate("date", "2013-10-5" , Map[String, String]()) mustEqual List(("date", "date must be '\\d{4}-\\d{2}-\\d{2}'."))
//    }
    it("should convert date string ") {
      val result = date().convert("2013-10-05", null)
      assert(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(result) == "2013-10-05 00:00:00")
    }
  }

}
