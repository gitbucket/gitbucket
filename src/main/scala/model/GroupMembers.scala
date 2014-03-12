package model

trait GroupMemberComponent { self: Profile =>
  import profile.simple._

  object GroupMembers extends Table[GroupMember]("GROUP_MEMBER") {
    def groupName = column[String]("GROUP_NAME", O PrimaryKey)
    def userName = column[String]("USER_NAME", O PrimaryKey)
    def isManager = column[Boolean]("MANAGER")
    def * = groupName ~ userName ~ isManager <> (GroupMember, GroupMember.unapply _)
  }
}

case class GroupMember(
  groupName: String,
  userName: String,
  isManager: Boolean
)