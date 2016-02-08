package gitbucket.core.api

case class CreateAUser (
  userName: String,
  password: String,
  fullName: String,
  mailAddress: String,
  url: Option[String] = None,
  fileId: Option[String] = None
  ) {
  def isValid: Boolean = {
    userName.length <= 40 &&
      userName.matches("[a-zA-Z0-9\\@\\-\\+_.]+") &&
      !userName.startsWith("_") &&
      !userName.startsWith("-") &&
    // password rule
      password.length > 8
  }
}

