package gitbucket.core.api

case class CreateAGroup (
                          groupName: String,
                          url: Option[String] = None,
                          fileId: Option[String] = None,
                          members: List[gMember]
                       ) {
  def isValid: Boolean = {
    groupName.length <= 100 &&
      groupName.matches("[a-zA-Z0-9\\s\\-\\+_.]+") &&
      !groupName.startsWith("_") &&
      !groupName.startsWith("-") &&
      members.length > 0 && members.filter(m => m.isManager).size > 0
  }
}

case class gMember (name: String, isManager: Boolean)
