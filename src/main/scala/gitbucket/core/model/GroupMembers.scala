package gitbucket.core.model

trait GroupMemberComponent { self: Profile =>
  import profile.simple._

  lazy val GroupMembers = TableQuery[GroupMembers]

  class GroupMembers(tag: Tag) extends Table[GroupMember](tag, "GROUP_MEMBER") {
    val groupName = column[String]("GROUP_NAME", O PrimaryKey)
    val userName = column[String]("USER_NAME", O PrimaryKey)
    val isManager = column[Boolean]("MANAGER")
    def * = (groupName, userName, isManager) <> (GroupMember.tupled, GroupMember.unapply)
  }
}

case class GroupMember(
  groupName: String,
  userName: String,
  isManager: Boolean
)
