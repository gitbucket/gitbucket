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

  "encodeCompareBranch" should {
    "not uri encode /" in {
      helpers.encodeCompareBranch("foo/bar#baz") mustEqual "foo/bar%23baz"
    }

    "not uri encode :" in {
      helpers.encodeCompareBranch("foo:bar#baz") mustEqual "foo:bar%23baz"
    }

    "uri encode special characters" in {
      helpers.encodeCompareBranch("!#$&'()+,;=?@[]") mustEqual "%21%23%24%26%27%28%29%2B%2C%3B%3D%3F%40%5B%5D"
    }
  }
}
