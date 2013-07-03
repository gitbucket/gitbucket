package util

import JGitUtil.RepositoryInfo
import app.ControllerBase
import service._

/**
 * Allows only the repository owner and administrators.
 */
trait OwnerOnlyAuthenticator { self: ControllerBase =>
  protected def ownerOnly(action: => Any) = { authenticate(action) }
  protected def ownerOnly[T](action: T => Any) = (form: T) => authenticate({action(form)})

  private def authenticate(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) if(x.isAdmin) => action
        case Some(x) if(request.getRequestURI.split("/")(1) == x.userName) => action
        case _ => Unauthorized()
      }
    }
  }
}

/**
 * Allows only signed in users.
 */
trait UsersOnlyAuthenticator { self: ControllerBase =>
  protected def usersOnly(action: => Any) = { authenticate(action) }
  protected def usersOnly[T](action: T => Any) = (form: T) => authenticate({action(form)})

  private def authenticate(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) => action
        case None => Unauthorized()
      }
    }
  }
}

/**
 * Allows only administrators.
 */
trait AdminOnlyAuthenticator { self: ControllerBase =>

  protected def adminOnly(action: => Any) = { authenticate(action) }
  protected def adminOnly[T](action: T => Any) = (form: T) => authenticate({action(form)})

  private def authenticate(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) if(x.isAdmin) => action
        case _ => Unauthorized()
      }
    }
  }
}

/**
 * Allows only collaborators and administrators.
 */
trait CollaboratorsAuthenticator { self: ControllerBase with RepositoryService =>

  protected def collaboratorsOnly(action: (RepositoryInfo) => Any) =
    (repository: RepositoryInfo) => authenticate({action(repository)})

  protected def collaboratorsOnly[T](action: (RepositoryInfo, T) => Any) =
    (repository: RepositoryInfo, form: T) => authenticate({action(repository, form)})

  private def authenticate(action: => Any) = {
    {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      getRepository(paths(1), paths(2), baseUrl).map { _ =>
        context.loginAccount match {
          case Some(x) if(x.isAdmin) => action
          case Some(x) if(paths(1) == x.userName) => action
          case Some(x) if(getCollaborators(paths(1), paths(2)).contains(x.userName)) => action
          case _ => Unauthorized()
        }
      } getOrElse NotFound()
    }
  }
}

/**
 * Allows only the repository owner and administrators.
 */
trait ReferrerAuthenticator { self: ControllerBase with RepositoryService =>

  protected def referrersOnly(action: (RepositoryInfo) => Any) =
    (repository: RepositoryInfo) => authenticate({action(repository)})

  protected def referrersOnly[T](action: (RepositoryInfo, T) => Any) =
    (repository: RepositoryInfo, form: T) => authenticate({action(repository, form)})

  private def authenticate(action: => Any) = {
    {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      getRepository(paths(1), paths(2), baseUrl) match {
        case None => NotFound()
        case Some(repository) =>
          if(!repository.repository.isPrivate){
            action
          } else {
            context.loginAccount match {
              case Some(x) if(x.isAdmin) => action
              case Some(x) if(paths(1) == x.userName) => action
              case Some(x) if(getCollaborators(paths(1), paths(2)).contains(x.userName)) => action
              case _ => Unauthorized()
            }
          }
      }
    }
  }
}

/**
 * Allows only signed in users which can access the repository.
 */
trait ReadableUsersAuthenticator { self: ControllerBase with RepositoryService =>

  protected def readableUsersOnly(action: (RepositoryInfo) => Any) =
    (repository: RepositoryInfo) => authenticate({action(repository)})

  protected def readableUsersOnly[T](action: (RepositoryInfo, T) => Any) =
    (repository: RepositoryInfo, form: T) => authenticate({action(repository, form)})

  private def authenticate(action: => Any) = {
    {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      getRepository(paths(1), paths(2), baseUrl) match {
        case None => NotFound()
        case Some(repository) => context.loginAccount match {
          case Some(x) if(x.isAdmin) => action
          case Some(x) if(!repository.repository.isPrivate) => action
          case Some(x) if(paths(1) == x.userName) => action
          case Some(x) if(getCollaborators(paths(1), paths(2)).contains(x.userName)) => action
          case _ => Unauthorized()
        }
      }
    }
  }
}
