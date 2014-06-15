package object model extends {
  // TODO
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
  with WebHookComponent with Profile {
  /**
   * Returns system date.
   */
  def currentDate = new java.util.Date()
}
