package util

import org.specs2.mutable._

class ValidationsSpec extends Specification with Validations {

  "identifier" should {
    "validate id string " in {
      identifier.validate("id", "aa_ZZ-01") mustEqual None
      identifier.validate("id", "_aaaa") mustEqual Some("id starts with invalid character.")
      identifier.validate("id", "-aaaa") mustEqual Some("id starts with invalid character.")
      identifier.validate("id", "aa_ZZ#01") mustEqual Some("id contains invalid character.")
    }
  }

  "color" should {
    "validate color string " in {
      color.validate("color", "#88aaff") mustEqual None
      color.validate("color", "#gghhii") mustEqual Some("color must be '#[0-9a-fA-F]{6}'.")
    }
  }

  "date" should {
//    "validate date string " in {
//      date().validate("date", "2013-10-05", Map[String, String]()) mustEqual Nil
//      date().validate("date", "2013-10-5" , Map[String, String]()) mustEqual List(("date", "date must be '\\d{4}-\\d{2}-\\d{2}'."))
//    }
    "convert date string " in {
      val result = date().convert("2013-10-05")
      new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(result) mustEqual "2013-10-05 00:00:00"
    }
  }

}
