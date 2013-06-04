package util

import app.ControllerBase
import service._
import org.scalatra._

/**
 * Allows only the repository owner and administrators.
 */
trait OwnerOnlyAuthenticator { self: ControllerBase =>
  protected def ownerOnly(action: => Any) = { authenticate(action) }
  protected def ownerOnly[T](action: T => Any) = (form: T) => authenticate({action(form)})

  private def authenticate(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) if(x.userType == AccountService.Administrator) => action
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
        case Some(x) if(x.userType == AccountService.Administrator) => action
        case _ => Unauthorized()
      }
    }
  }
}

/**
 * Allows only collaborators and administrators.
 */
trait WritableRepositoryAuthenticator { self: ControllerBase with RepositoryService =>
  protected def writableRepository(action: => Any) = { authenticate(action) }
  protected def writableRepository[T](action: T => Any) = (form: T) => authenticate({action(form)})

  private def authenticate(action: => Any) = {
      val paths = request.getRequestURI.split("/")
      context.loginAccount match {
        case Some(x) if(x.userType == AccountService.Administrator) => action
        case Some(x) if(paths(1) == x.userName) => action
        case Some(x) if(getCollaborators(paths(1), paths(2)).contains(x.userName)) => action
        case _ => Unauthorized()
      }
  }
}

/**
 * Allows only the repository owner and administrators.
 */
trait ReadableRepositoryAuthenticator { self: ControllerBase with RepositoryService =>
  protected def readableRepository(action: => Any) = { authenticate(action) }
  protected def readableRepository[T](action: T => Any) = (form: T) => authenticate({action(form)})

  private def authenticate(action: => Any) = {
    {
      val paths = request.getRequestURI.split("/")
      val repository = getRepository(paths(1), paths(2), servletContext)
      if(repository.get.repository.repositoryType == RepositoryService.Public){
        action
      } else {
        context.loginAccount match {
          case Some(x) if(x.userType == AccountService.Administrator) => action
          case Some(x) if(paths(1) == x.userName) => action
          case Some(x) if(getCollaborators(paths(1), paths(2)).contains(x.userName)) => action
          case _ => Unauthorized()
        }
      }
    }
  }
}
