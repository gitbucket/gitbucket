package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryCreationService, RepositoryService}
import gitbucket.core.servlet.Database
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util._
import gitbucket.core.util.Implicits._
import gitbucket.core.model.Profile.profile.blockingApi._
import org.eclipse.jgit.api.Git

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Using

trait ApiRepositoryControllerBase extends ControllerBase {
  self: RepositoryService
    with RepositoryCreationService
    with AccountService
    with OwnerAuthenticator
    with UsersAuthenticator
    with GroupManagerAuthenticator
    with ReferrerAuthenticator
    with ReadableUsersAuthenticator
    with WritableUsersAuthenticator =>

  /**
   * i. List your repositories
   * https://developer.github.com/v3/repos/#list-your-repositories
   */
  get("/api/v3/user/repos")(usersOnly {
    JsonFormat(getVisibleRepositories(context.loginAccount, Option(context.loginAccount.get.userName)).map { r =>
      ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  })

  /**
   * ii. List user repositories
   * https://developer.github.com/v3/repos/#list-user-repositories
   */
  get("/api/v3/users/:userName/repos") {
    JsonFormat(getVisibleRepositories(context.loginAccount, Some(params("userName"))).map { r =>
      ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  }

  /**
   * iii. List organization repositories
   * https://developer.github.com/v3/repos/#list-organization-repositories
   */
  get("/api/v3/orgs/:orgName/repos") {
    JsonFormat(getVisibleRepositories(context.loginAccount, Some(params("orgName"))).map { r =>
      ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  }

  /**
   * iv. List all public repositories
   * https://developer.github.com/v3/repos/#list-public-repositories
   */
  get("/api/v3/repositories") {
    JsonFormat(getPublicRepositories().map { r =>
      ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  }

  /*
   * v. Create
   * https://developer.github.com/v3/repos/#create
   * Implemented with two methods (user/orgs)
   */

  /**
   * Create user repository
   * https://developer.github.com/v3/repos/#create
   */
  post("/api/v3/user/repos")(usersOnly {
    val owner = context.loginAccount.get.userName
    (for {
      data <- extractFromJsonBody[CreateARepository] if data.isValid
    } yield {
      LockUtil.lock(s"${owner}/${data.name}") {
        if (getRepository(owner, data.name).isEmpty) {
          val f = createRepository(
            context.loginAccount.get,
            owner,
            data.name,
            data.description,
            data.`private`,
            data.auto_init
          )
          Await.result(f, Duration.Inf)

          val repository = Database() withTransaction { session =>
            getRepository(owner, data.name)(session).get
          }
          JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(owner).get)))
        } else {
          ApiError(
            "A repository with this name already exists on this account",
            Some("https://developer.github.com/v3/repos/#create")
          )
        }
      }
    }) getOrElse NotFound()
  })

  /**
   * Create group repository
   * https://developer.github.com/v3/repos/#create
   */
  post("/api/v3/orgs/:org/repos")(managersOnly {
    val groupName = params("org")
    (for {
      data <- extractFromJsonBody[CreateARepository] if data.isValid
    } yield {
      LockUtil.lock(s"${groupName}/${data.name}") {
        if (getRepository(groupName, data.name).isEmpty) {
          val f = createRepository(
            context.loginAccount.get,
            groupName,
            data.name,
            data.description,
            data.`private`,
            data.auto_init
          )
          Await.result(f, Duration.Inf)
          val repository = Database() withTransaction { session =>
            getRepository(groupName, data.name).get
          }
          JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(groupName).get)))
        } else {
          ApiError(
            "A repository with this name already exists for this group",
            Some("https://developer.github.com/v3/repos/#create")
          )
        }
      }
    }) getOrElse NotFound()
  })

  /*
   * vi. Get
   * https://developer.github.com/v3/repos/#get
   */
  get("/api/v3/repos/:owner/:repository")(referrersOnly { repository =>
    JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(repository.owner).get)))
  })

  /*
   * vii. Edit
   * https://developer.github.com/v3/repos/#edit
   */

  /*
   * viii. List all topics for a repository
   * https://developer.github.com/v3/repos/#list-all-topics-for-a-repository
   */

  /*
   * ix. Replace all topics for a repository
   * https://developer.github.com/v3/repos/#replace-all-topics-for-a-repository
   */

  /*
   * x. List contributors
   * https://developer.github.com/v3/repos/#list-contributors
   */

  /*
   * xi. List languages
   * https://developer.github.com/v3/repos/#list-languages
   */

  /*
   * xii. List teams
   * https://developer.github.com/v3/repos/#list-teams
   */

  /*
   * xiii. List repository tags
   * https://docs.github.com/en/rest/reference/repos#list-repository-tags
   */
  get("/api/v3/repos/:owner/:repository/tags")(referrersOnly { repository =>
    JsonFormat(
      repository.tags.map(tagInfo => ApiTag(tagInfo.name, RepositoryName(repository), tagInfo.id))
    )
  })

  /*
   * xiv. Delete a repository
   * https://developer.github.com/v3/repos/#delete-a-repository
   */

  /*
   * xv. Transfer a repository
   * https://developer.github.com/v3/repos/#transfer-a-repository
   */

  /**
   * non-GitHub compatible API for Jenkins-Plugin
   */
  get("/api/v3/repos/:owner/:repository/raw/*")(referrersOnly { repository =>
    val (id, path) = repository.splitPath(multiParams("splat").head)
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))

      getPathObjectId(git, path, revCommit).map { objectId =>
        responseRawFile(git, objectId, path, repository)
      } getOrElse NotFound()
    }
  })
}
