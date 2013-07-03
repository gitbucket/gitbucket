package util

import app.ControllerBase
import service._
import RepositoryService.RepositoryInfo

/**
 * Allows only oneself and administrators.
 */
trait OneselfAuthenticator { self: ControllerBase =>
  protected def oneselfOnly(action: => Any) = { authenticate(action) }
  protected def oneselfOnly[T](action: T => Any) = (form: T) => { authenticate(action(form)) }

  private def authenticate(action: => Any) = {
    {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      context.loginAccount match {
        case Some(x) if(x.isAdmin) => action
        case Some(x) if(paths(1) == x.userName) => action
        case _ => Unauthorized()
      }
    }
  }
}

/**
 * Allows only the repository owner and administrators.
 */
trait OwnerAuthenticator { self: ControllerBase with RepositoryService =>
  protected def ownerOnly(action: (RepositoryInfo) => Any) = { authenticate(action) }
  protected def ownerOnly[T](action: (T, RepositoryInfo) => Any) = (form: T) => { authenticate(action(form, _)) }

  private def authenticate(action: (RepositoryInfo) => Any) = {
    {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      getRepository(paths(1), paths(2), baseUrl).map { repository =>
        context.loginAccount match {
          case Some(x) if(x.isAdmin) => action(repository)
          case Some(x) if(repository.owner == x.userName) => action(repository)
          case _ => Unauthorized()
        }
      } getOrElse NotFound()
    }
  }
}

/**
 * Allows only signed in users.
 */
trait UsersAuthenticator { self: ControllerBase =>
  protected def usersOnly(action: => Any) = { authenticate(action) }
  protected def usersOnly[T](action: T => Any) = (form: T) => { authenticate(action(form)) }

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
trait AdminAuthenticator { self: ControllerBase =>
  protected def adminOnly(action: => Any) = { authenticate(action) }
  protected def adminOnly[T](action: T => Any) = (form: T) => { authenticate(action(form)) }

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
  protected def collaboratorsOnly(action: (RepositoryInfo) => Any) = { authenticate(action) }
  protected def collaboratorsOnly[T](action: (T, RepositoryInfo) => Any) = (form: T) => { authenticate(action(form, _)) }

  private def authenticate(action: (RepositoryInfo) => Any) = {
    {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      getRepository(paths(1), paths(2), baseUrl).map { repository =>
        context.loginAccount match {
          case Some(x) if(x.isAdmin) => action(repository)
          case Some(x) if(paths(1) == x.userName) => action(repository)
          case Some(x) if(getCollaborators(paths(1), paths(2)).contains(x.userName)) => action(repository)
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
  protected def referrersOnly(action: (RepositoryInfo) => Any) = { authenticate(action) }
  protected def referrersOnly[T](action: (T, RepositoryInfo) => Any) = (form: T) => { authenticate(action(form, _)) }

  private def authenticate(action: (RepositoryInfo) => Any) = {
    {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      getRepository(paths(1), paths(2), baseUrl).map { repository =>
        if(!repository.repository.isPrivate){
          action(repository)
        } else {
          context.loginAccount match {
            case Some(x) if(x.isAdmin) => action(repository)
            case Some(x) if(paths(1) == x.userName) => action(repository)
            case Some(x) if(getCollaborators(paths(1), paths(2)).contains(x.userName)) => action(repository)
            case _ => Unauthorized()
          }
        }
      } getOrElse NotFound()
    }
  }
}

/**
 * Allows only signed in users which can access the repository.
 */
trait ReadableUsersAuthenticator { self: ControllerBase with RepositoryService =>
  protected def readableUsersOnly(action: (RepositoryInfo) => Any) = { authenticate(action) }
  protected def readableUsersOnly[T](action: (T, RepositoryInfo) => Any) = (form: T) => { authenticate(action(form, _)) }

  private def authenticate(action: (RepositoryInfo) => Any) = {
    {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      getRepository(paths(1), paths(2), baseUrl).map { repository =>
        context.loginAccount match {
          case Some(x) if(x.isAdmin) => action(repository)
          case Some(x) if(!repository.repository.isPrivate) => action(repository)
          case Some(x) if(paths(1) == x.userName) => action(repository)
          case Some(x) if(getCollaborators(paths(1), paths(2)).contains(x.userName)) => action(repository)
          case _ => Unauthorized()
        }
      } getOrElse NotFound()
    }
  }
}
