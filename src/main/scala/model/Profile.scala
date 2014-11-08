package model

trait Profile {
  val profile: slick.driver.JdbcProfile
  import profile.simple._

  // java.util.Date Mapped Column Types
  implicit val dateColumnType = MappedColumnType.base[java.util.Date, java.sql.Timestamp](
      d => new java.sql.Timestamp(d.getTime),
      t => new java.util.Date(t.getTime)
  )

  implicit class RichColumn(c1: Column[Boolean]){
    def &&(c2: => Column[Boolean], guard: => Boolean): Column[Boolean] = if(guard) c1 && c2 else c1
  }

}

object Profile extends {
  val profile = slick.driver.H2Driver

} with AccountComponent
  with ActivityComponent
  with CollaboratorComponent
  with GroupMemberComponent
  with IssueComponent
  with IssueCommentComponent
  with IssueLabelComponent
  with LabelComponent
  with MilestoneComponent
  with PullRequestComponent
  with RepositoryComponent
  with SshKeyComponent
  with WebHookComponent
  with PluginComponent with Profile {

  /**
   * Returns system date.
   */
  def currentDate = new java.util.Date()

}
