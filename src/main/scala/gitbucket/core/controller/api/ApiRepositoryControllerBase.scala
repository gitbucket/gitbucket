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
import org.scalatra.{Accepted, Forbidden, UnprocessableEntity}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Using

trait ApiRepositoryControllerBase extends ControllerBase {
  self: RepositoryService & ApiGitReferenceControllerBase & RepositoryCreationService & AccountService &
    OwnerAuthenticator & UsersAuthenticator & GroupManagerAuthenticator & ReferrerAuthenticator &
    ReadableUsersAuthenticator & WritableUsersAuthenticator =>

  private val createForkDocumentationUrl = "https://docs.github.com/en/rest/repos/forks#create-a-fork"
  private def createForkValidationError(message: String) =
    ApiError(message, Some(createForkDocumentationUrl))

  /**
   * i. List your repositories
   * https://docs.github.com/en/rest/reference/repos#list-repositories-for-the-authenticated-user
   */
  get("/api/v3/user/repos")(usersOnly {
    JsonFormat(getVisibleRepositories(context.loginAccount, Option(context.loginAccount.get.userName)).map { r =>
      ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  })

  /**
   * ii. List user repositories
   * https://docs.github.com/en/rest/reference/repos#list-repositories-for-a-user
   */
  get("/api/v3/users/:userName/repos") {
    JsonFormat(getVisibleRepositories(context.loginAccount, Some(params("userName"))).map { r =>
      ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  }

  /**
   * iii. List organization repositories
   * https://docs.github.com/en/rest/reference/repos#list-organization-repositories
   */
  get("/api/v3/orgs/:orgName/repos") {
    JsonFormat(getVisibleRepositories(context.loginAccount, Some(params("orgName"))).map { r =>
      ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  }

  /**
   * iv. List all public repositories
   * https://docs.github.com/en/rest/reference/repos#list-public-repositories
   */
  get("/api/v3/repositories") {
    JsonFormat(getPublicRepositories().map { r =>
      ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  }

  /*
   * v. Create
   * Implemented with two methods (user/orgs)
   */

  /**
   * Create user repository
   * https://docs.github.com/en/rest/reference/repos#create-a-repository-for-the-authenticated-user
   */
  post("/api/v3/user/repos")(usersOnly {
    val owner = context.loginAccount.get.userName
    (for {
      data <- extractFromJsonBody[CreateARepository] if data.isValid
    } yield {
      LockUtil.lock(s"${owner}/${data.name}") {
        if (getRepository(owner, data.name).isDefined) {
          ApiError(
            "A repository with this name already exists on this account",
            Some("https://developer.github.com/v3/repos/#create")
          )
        } else {
          val f = createRepository(
            context.loginAccount.get,
            owner,
            data.name,
            data.description,
            data.`private`,
            data.auto_init,
            context.settings.defaultBranch
          )
          Await.result(f, Duration.Inf)

          val repository = Database() withTransaction { session =>
            getRepository(owner, data.name)(session).get
          }
          JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(owner).get)))
        }
      }
    }) getOrElse NotFound()
  })

  /**
   * Create group repository
   * https://docs.github.com/en/rest/reference/repos#create-an-organization-repository
   */
  post("/api/v3/orgs/:org/repos")(usersOnly {
    val groupName = params("org")
    (for {
      data <- extractFromJsonBody[CreateARepository] if data.isValid
    } yield {
      LockUtil.lock(s"${groupName}/${data.name}") {
        if (getRepository(groupName, data.name).isDefined) {
          ApiError(
            "A repository with this name already exists for this group",
            Some("https://developer.github.com/v3/repos/#create")
          )
        } else if (!canCreateRepository(groupName, context.loginAccount.get)) {
          Forbidden()
        } else {
          val f = createRepository(
            context.loginAccount.get,
            groupName,
            data.name,
            data.description,
            data.`private`,
            data.auto_init,
            context.settings.defaultBranch
          )
          Await.result(f, Duration.Inf)
          val repository = Database() withTransaction { session =>
            getRepository(groupName, data.name).get
          }
          JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(groupName).get)))
        }
      }
    }) getOrElse NotFound()
  })

  /*
   * vi. Get
   * https://docs.github.com/en/rest/reference/repos#get-a-repository
   */
  get("/api/v3/repos/:owner/:repository")(referrersOnly { repository =>
    JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(repository.owner).get)))
  })

  /*
   * vii. Edit
   * https://docs.github.com/en/rest/reference/repos#update-a-repository
   */

  /*
   * viii. List all topics for a repository
   * https://docs.github.com/en/rest/reference/repos#get-all-repository-topics
   */

  /*
   * ix. Replace all topics for a repository
   * https://docs.github.com/en/rest/reference/repos#replace-all-repository-topics
   */

  /*
   * x. List contributors
   * https://docs.github.com/en/rest/reference/repos#list-repository-contributors
   */

  /*
   * xi. List languages
   * https://docs.github.com/en/rest/reference/repos#list-repository-languages
   */

  /*
   * xii. List teams
   * https://docs.github.com/en/rest/reference/repos#list-repository-teams
   */

  /*
   * xiii. List repository tags
   * https://docs.github.com/en/rest/reference/repos#list-repository-tags
   */
  get("/api/v3/repos/:owner/:repository/tags")(referrersOnly { repository =>
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      JsonFormat(
        repository.tags.map(tagInfo => ApiTag(tagInfo.name, RepositoryName(repository), tagInfo.commitId))
      )
    }
  })

  /*
   * xiv. Delete a repository
   * https://docs.github.com/en/rest/reference/repos#delete-a-repository
   */

  /*
   * xv. Transfer a repository
   * https://docs.github.com/en/rest/reference/repos#transfer-a-repository
   */

  /**
   * xvi. Get a repository by its numeric id
   * https://docs.github.com/en/rest/repos/repos#get-a-repository
   */
  get("/api/v3/repositories/:id") {
    params("id").toLongOption.flatMap { id =>
      getRepositoryById(id)
        .filter(r => isReadable(r.repository, context.loginAccount))
        .map(r => JsonFormat(ApiRepository(r, ApiUser(getAccountByUserName(r.owner).get))))
    } getOrElse NotFound()
  }

  /**
   * xvii. Fork a repository
   * https://docs.github.com/en/rest/repos/forks#create-a-fork
   */
  post("/api/v3/repos/:owner/:repository/forks")(usersOnly {
    val loginAccount = context.loginAccount.get
    val owner = params("owner")
    val repositoryName = params("repository")

    getRepository(owner, repositoryName).filter(r => isReadable(r.repository, Some(loginAccount))) match {
      case None             => NotFound()
      case Some(repository) =>
        if (!repository.repository.options.allowFork) {
          Forbidden()
        } else {
          val organization = extractFromJsonBody[CreateAFork].flatMap(_.organization)
          val targetAccount = organization.getOrElse(loginAccount.userName)

          if (organization.isDefined && getAccountByUserName(targetAccount).isEmpty) {
            UnprocessableEntity(createForkValidationError("The specified organization does not exist."))
          } else if (targetAccount == owner) {
            UnprocessableEntity(createForkValidationError("A user cannot fork their own repository."))
          } else {
            val originOwner = repository.repository.originUserName.getOrElse(owner)
            val originName = repository.repository.originRepositoryName.getOrElse(repositoryName)
            getRepository(targetAccount, repositoryName) match {
              case Some(existing) =>
                if (
                  existing.repository.originUserName.contains(originOwner) &&
                  existing.repository.originRepositoryName.contains(originName)
                )
                  Accepted(JsonFormat(ApiRepository(existing, ApiUser(getAccountByUserName(targetAccount).get))))
                else
                  UnprocessableEntity(createForkValidationError("A repository with the same name already exists."))
              case None =>
                if (canCreateRepository(targetAccount, loginAccount)) {
                  Await.result(forkRepository(targetAccount, repository, loginAccount.userName), Duration.Inf)
                  val fork = Database() withTransaction { implicit session =>
                    getRepository(targetAccount, repositoryName)(session).get
                  }
                  Accepted(JsonFormat(ApiRepository(fork, ApiUser(getAccountByUserName(targetAccount).get))))
                } else
                  Forbidden()
            }
          }
        }
    }
  })

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
