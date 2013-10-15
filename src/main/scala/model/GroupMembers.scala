package model

import scala.slick.driver.H2Driver.simple._

object GroupMembers extends Table[GroupMember]("GROUP_MEMBER") {
  def groupName = column[String]("GROUP_NAME", O PrimaryKey)
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def * = groupName ~ userName <> (GroupMember, GroupMember.unapply _)
}

case class GroupMember(
  groupName: String,
  userName: String
)