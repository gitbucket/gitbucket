package gitbucket.core.view

import org.specs2.mutable._
import gitbucket.core.model.Repository
import java.util.Date

class HelpersSpec extends Specification {
  def repository(defaultBranch: String, parentUserName: Option[String]) =
    Repository(
      userName = "some-user",
      repositoryName = "some-repo",
      isPrivate = false,
      description = None,
      defaultBranch = defaultBranch,
      parentUserName = parentUserName,
      parentRepositoryName = Some("some-repo"),
      registeredDate = new Date(),
      updatedDate = new Date(),
      lastActivityDate = new Date(),
      originUserName = Some("some-other-user"),
      originRepositoryName = Some("some-repo")
    )

  "repositoryDefaultCompareOrigin" should {
    "return default branch when not fork" in {
      val repo = repository("master", None)

      helpers.repositoryDefaultCompareOrigin(repo) mustEqual "master"
    }

    "return [upstream]:[branch] when a fork" in {
      val repo = repository("some-branch", Some("parent-user"))

      helpers.repositoryDefaultCompareOrigin(repo) mustEqual "parent-user:some-branch"
    }
  }
}
