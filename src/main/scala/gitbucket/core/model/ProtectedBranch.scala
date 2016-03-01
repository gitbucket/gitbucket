package gitbucket.core.model

import scala.slick.lifted.MappedTo
import scala.slick.jdbc._

trait ProtectedBranchComponent extends TemplateComponent { self: Profile =>
  import profile.simple._
  import self._

  lazy val ProtectedBranches = TableQuery[ProtectedBranches]
  class ProtectedBranches(tag: Tag) extends Table[ProtectedBranch](tag, "PROTECTED_BRANCH") with BranchTemplate {
    val statusCheckAdmin = column[Boolean]("STATUS_CHECK_ADMIN")
    def * = (userName, repositoryName, branch, statusCheckAdmin) <> (ProtectedBranch.tupled, ProtectedBranch.unapply)
    def byPrimaryKey(userName: String, repositoryName: String, branch: String) = byBranch(userName, repositoryName, branch)
    def byPrimaryKey(userName: Column[String], repositoryName: Column[String], branch: Column[String]) = byBranch(userName, repositoryName, branch)
  }

  lazy val ProtectedBranchContexts = TableQuery[ProtectedBranchContexts]
  class ProtectedBranchContexts(tag: Tag) extends Table[ProtectedBranchContext](tag, "PROTECTED_BRANCH_REQUIRE_CONTEXT") with BranchTemplate {
    val context = column[String]("CONTEXT")
    def * = (userName, repositoryName, branch, context) <> (ProtectedBranchContext.tupled, ProtectedBranchContext.unapply)
  }
}


case class ProtectedBranch(
  userName: String,
  repositoryName: String,
  branch: String,
  statusCheckAdmin: Boolean)


case class ProtectedBranchContext(
  userName: String,
  repositoryName: String,
  branch: String,
  context: String)
