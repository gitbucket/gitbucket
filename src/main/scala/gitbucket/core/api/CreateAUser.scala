package gitbucket.core.api

import scala.util.matching.Regex

case class CreateAUser (
  userName: String,
  password: String,
  fullName: String,
  mailAddress: String,
  url: Option[String] = None,
  fileId: Option[String] = None
  ) {
  def isValid: Boolean = {
    val pattern = new Regex("^.*(?=.{8,})(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[\\d\\W]).*$")
    userName.length <= 40 &&
      userName.matches("[a-zA-Z0-9\\@\\-\\+_.]+") &&
      !userName.startsWith("_") &&
      !userName.startsWith("-") &&
    // password rule
      password.length > 8 && pattern.findAllIn(password).toList.size > 0

  }


}

case class ModifyAUser (
                         userName: String,
                         password: Option[String] = None,
                         fullName: Option[String] = None,
                         mailAddress: String,
                         url: Option[String] = None,
                         fileId: Option[String] = None
                       ) {
  def isValid: Boolean = {
    val pattern = new Regex("^.*(?=.{8,})(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[\\d\\W]).*$")
    val isPasswordOK = password match {
      case Some(p) => p.length > 8 && pattern.findAllIn(p).toList.size > 0
      case None => true
    }
    userName.length <= 40 &&
      userName.matches("[a-zA-Z0-9\\@\\-\\+_.]+") &&
      !userName.startsWith("_") &&
      !userName.startsWith("-") &&
      // password rule
      isPasswordOK

  }
}

