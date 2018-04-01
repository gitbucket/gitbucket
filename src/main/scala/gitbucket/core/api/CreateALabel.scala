package gitbucket.core.api

/**
 * https://developer.github.com/v3/issues/labels/#create-a-label
 * api form
 */
case class CreateALabel(
  name: String,
  color: String
) {
  def isValid: Boolean = {
    name.length <= 100 &&
    !name.startsWith("_") &&
    !name.startsWith("-") &&
    color.length == 6 &&
    color.matches("[a-fA-F0-9+_.]+")
  }
}
