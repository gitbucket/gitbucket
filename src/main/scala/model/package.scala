package object model extends Profile
  with AccountComponent
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
  with WebHookComponent {

  /**
   * Returns system date.
   */
  def currentDate = new java.util.Date()

}
