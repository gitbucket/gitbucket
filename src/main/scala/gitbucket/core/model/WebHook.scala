package gitbucket.core.model

trait WebHookComponent extends TemplateComponent { self: Profile =>
  import profile.simple._

  lazy val WebHooks = TableQuery[WebHooks]

  class WebHooks(tag: Tag) extends Table[WebHook](tag, "WEB_HOOK") with BasicTemplate {
    val url = column[String]("URL")
    val token = column[Option[String]]("TOKEN", O.Nullable)
    def * = (userName, repositoryName, url, token) <> ((WebHook.apply _).tupled, WebHook.unapply)

    def byPrimaryKey(owner: String, repository: String, url: String) = byRepository(owner, repository) && (this.url === url.bind)
  }
}

case class WebHook(
  userName: String,
  repositoryName: String,
  url: String,
  token: Option[String]
)

object WebHook {
  sealed class Event(var name: String)
  case object CommitComment extends Event("commit_comment")
  case object Create extends Event("create")
  case object Delete extends Event("delete")
  case object Deployment extends Event("deployment")
  case object DeploymentStatus extends Event("deployment_status")
  case object Fork extends Event("fork")
  case object Gollum extends Event("gollum")
  case object IssueComment extends Event("issue_comment")
  case object Issues extends Event("issues")
  case object Member extends Event("member")
  case object PageBuild extends Event("page_build")
  case object Public extends Event("public")
  case object PullRequest extends Event("pull_request")
  case object PullRequestReviewComment extends Event("pull_request_review_comment")
  case object Push extends Event("push")
  case object Release extends Event("release")
  case object Status extends Event("status")
  case object TeamAdd extends Event("team_add")
  case object Watch extends Event("watch")
  object Event{
    val values = List(CommitComment,Create,Delete,Deployment,DeploymentStatus,Fork,Gollum,IssueComment,Issues,Member,PageBuild,Public,PullRequest,PullRequestReviewComment,Push,Release,Status,TeamAdd,Watch)
    private val map:Map[String,Event] = values.map(e => e.name -> e).toMap
    def valueOf(name: String): Event = map(name)
    def valueOpt(name: String): Option[Event] = map.get(name)
  }
}
