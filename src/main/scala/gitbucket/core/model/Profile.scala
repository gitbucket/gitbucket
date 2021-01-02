package gitbucket.core.model

import com.github.takezoe.slick.blocking.BlockingJdbcProfile
import gitbucket.core.util.DatabaseConfig

trait Profile {
  val profile: BlockingJdbcProfile
  import profile.blockingApi._

  /**
   * java.util.Date Mapped Column Types
   */
  implicit val dateColumnType = MappedColumnType.base[java.util.Date, java.sql.Timestamp](
    d => new java.sql.Timestamp(d.getTime),
    t => new java.util.Date(t.getTime)
  )

  /**
   * WebHookBase.Event Column Types
   */
  implicit val eventColumnType = MappedColumnType.base[WebHook.Event, String](_.name, WebHook.Event.valueOf(_))

  /**
   * Extends Column to add conditional condition
   */
  implicit class RichColumn(c1: Rep[Boolean]) {
    def &&(c2: => Rep[Boolean], guard: => Boolean): Rep[Boolean] = if (guard) c1 && c2 else c1
  }

  /**
   * Returns system date.
   */
  def currentDate = new java.util.Date()

}

trait ProfileProvider { self: Profile =>

  lazy val profile = DatabaseConfig.slickDriver

}

trait CoreProfile
    extends ProfileProvider
    with Profile
    with AccessTokenComponent
    with AccountComponent
    with ActivityComponent
    with CollaboratorComponent
    with CommitCommentComponent
    with CommitStatusComponent
    with GroupMemberComponent
    with IssueComponent
    with IssueCommentComponent
    with IssueLabelComponent
    with LabelComponent
    with PriorityComponent
    with MilestoneComponent
    with PullRequestComponent
    with RepositoryComponent
    with SshKeyComponent
    with GpgKeyComponent
    with RepositoryWebHookComponent
    with RepositoryWebHookEventComponent
    with AccountWebHookComponent
    with AccountWebHookEventComponent
    with AccountFederationComponent
    with ProtectedBranchComponent
    with DeployKeyComponent
    with ReleaseTagComponent
    with ReleaseAssetComponent
    with AccountExtraMailAddressComponent
    with AccountPreferenceComponent

object Profile extends CoreProfile
