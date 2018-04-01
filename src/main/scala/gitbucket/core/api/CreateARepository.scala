package gitbucket.core.api

/**
 * https://developer.github.com/v3/repos/#create
 * api form
 */
case class CreateARepository(
  name: String,
  description: Option[String],
  `private`: Boolean = false,
  auto_init: Boolean = false
) {
  def isValid: Boolean = {
    name.length <= 100 &&
    name.matches("[a-zA-Z0-9\\-\\+_.]+") &&
    !name.startsWith("_") &&
    !name.startsWith("-")
  }
}
