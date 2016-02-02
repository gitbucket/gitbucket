package gitbucket.core.api

case class ModifyGroupMembers (
                          members: List[String]
                        ) {
  def isValid: Boolean = {
      members.length > 0
  }
}
