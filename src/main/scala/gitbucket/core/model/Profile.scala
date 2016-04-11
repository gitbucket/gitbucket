package gitbucket.core.model

import gitbucket.core.util.DatabaseConfig


trait Profile {
  val profile: slick.driver.JdbcProfile
  import profile.simple._

  /**
   * java.util.Date Mapped Column Types
   */
  implicit val dateColumnType = MappedColumnType.base[java.util.Date, java.sql.Timestamp](
    d => new java.sql.Timestamp(d.getTime),
    t => new java.util.Date(t.getTime)
  )

  /**
   * Extends Column to add conditional condition
   */
  implicit class RichColumn(c1: Column[Boolean]){
    def &&(c2: => Column[Boolean], guard: => Boolean): Column[Boolean] = if(guard) c1 && c2 else c1
  }

  /**
   * Returns system date.
   */
  def currentDate = new java.util.Date()

}

trait ProfileProvider { self: Profile =>

  private lazy val url = DatabaseConfig.url

  lazy val profile = if(url.indexOf("h2") >= 0){
    slick.driver.H2Driver
  } else if(url.indexOf("mysql") >= 0) {
    slick.driver.MySQLDriver
  } else {
    throw new ExceptionInInitializerError(s"${url} is not unsupported.")
  }

}

trait CoreProfile extends ProfileProvider with Profile
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
  with MilestoneComponent
  with PullRequestComponent
  with RepositoryComponent
  with SshKeyComponent
  with WebHookComponent
  with WebHookEventComponent
  with ProtectedBranchComponent

object Profile extends CoreProfile
