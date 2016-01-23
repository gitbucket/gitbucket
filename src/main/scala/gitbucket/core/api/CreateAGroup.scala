package gitbucket.core.api

case class CreateAGroup (
                          groupName: String,
                          url: Option[String] = None,
                          fileId: Option[String] = None,
                          manager: String,
                          members: List[String]
                       ) {
  def isValid: Boolean = {
    groupName.length <= 100 &&
      groupName.matches("[a-zA-Z0-9\\-\\+_.]+") &&
      !groupName.startsWith("_") &&
      !groupName.startsWith("-") &&
      members.contains(manager) &&
      members.length > 0
  }
}
