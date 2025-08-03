package gitbucket.core.model

trait ProtectedBranchComponent extends TemplateComponent { self: Profile =>
  import profile.api.*

  lazy val ProtectedBranches = TableQuery[ProtectedBranches]
  class ProtectedBranches(tag: Tag) extends Table[ProtectedBranch](tag, "PROTECTED_BRANCH") with BranchTemplate {
    val statusCheckAdmin = column[Boolean]("STATUS_CHECK_ADMIN") // enforceAdmins
    val requiredStatusCheck = column[Boolean]("REQUIRED_STATUS_CHECK")
    val restrictions = column[Boolean]("RESTRICTIONS")
    def * =
      (userName, repositoryName, branch, statusCheckAdmin, requiredStatusCheck, restrictions).mapTo[ProtectedBranch]
    def byPrimaryKey(userName: String, repositoryName: String, branch: String): Rep[Boolean] =
      byBranch(userName, repositoryName, branch)
    def byPrimaryKey(userName: Rep[String], repositoryName: Rep[String], branch: Rep[String]): Rep[Boolean] =
      byBranch(userName, repositoryName, branch)
  }

  lazy val ProtectedBranchContexts = TableQuery[ProtectedBranchContexts]
  class ProtectedBranchContexts(tag: Tag)
      extends Table[ProtectedBranchContext](tag, "PROTECTED_BRANCH_REQUIRE_CONTEXT")
      with BranchTemplate {
    val context = column[String]("CONTEXT")
    def * =
      (userName, repositoryName, branch, context).mapTo[ProtectedBranchContext]
  }

  lazy val ProtectedBranchRestrictions = TableQuery[ProtectedBranchRestrictions]
  class ProtectedBranchRestrictions(tag: Tag)
      extends Table[ProtectedBranchRestriction](tag, "PROTECTED_BRANCH_RESTRICTION")
      with BranchTemplate {
    val allowedUser = column[String]("ALLOWED_USER")
    def * = (userName, repositoryName, branch, allowedUser).mapTo[ProtectedBranchRestriction]
    def byPrimaryKey(userName: String, repositoryName: String, branch: String, allowedUser: String): Rep[Boolean] =
      this.userName === userName.bind && this.repositoryName === repositoryName.bind && this.branch === branch.bind && this.allowedUser === allowedUser.bind
  }
}

case class ProtectedBranch(
  userName: String,
  repositoryName: String,
  branch: String,
  enforceAdmins: Boolean,
  requiredStatusCheck: Boolean,
  restrictions: Boolean
)

case class ProtectedBranchContext(userName: String, repositoryName: String, branch: String, context: String)

case class ProtectedBranchRestriction(userName: String, repositoryName: String, branch: String, allowedUser: String)
