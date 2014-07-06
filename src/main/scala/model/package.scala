package object model extends {
  // TODO [Slick 2.0]Should be configurable?
  val profile = slick.driver.H2Driver
  // TODO [Slick 2.0]To avoid compilation error about delete invocation. Why can't this error be resolved by import profile.simple._?
  val simple = profile.simple

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
