package util

import app.ControllerBase
import service._

/**
 * Allows only the repository owner and administrators.
 */
trait OwnerOnlyAuthenticator { self: ControllerBase =>

  protected def ownerOnly(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) if(x.userType == AccountService.Administrator) => action
        case Some(x) if(request.getRequestURI.split("/")(1) == x.userName) => action
        case _ => redirect("/signin")
      }
    }
  }

  protected def ownerOnly[T](action: T => Any) = {
    (form: T) => {
      context.loginAccount match {
        case Some(x) if(x.userType == AccountService.Administrator) => action(form)
        case Some(x) if(request.getRequestURI.split("/")(1) == x.userName) => action(form)
        case _ => redirect("/signin")
      }
    }
  }
}

/**
 * Allows only signed in users.
 */
trait UsersOnlyAuthenticator { self: ControllerBase =>

  protected def usersOnly(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) => action
        case None    => redirect("/signin")
      }
    }
  }

  protected def usersOnly[T](action: T => Any) = {
    (form: T) => {
      context.loginAccount match {
        case Some(x) => action(form)
        case None    => redirect("/signin")
      }
    }
  }
}

/**
 * Allows only collaborators and administrators.
 */
trait CollaboratorsOnlyAuthenticator { self: ControllerBase with RepositoryService =>

  protected def collaboratorsOnly(action: => Any) = {
    {
      context.loginAccount match {
        case Some(x) if(x.userType == AccountService.Administrator) => action
        case Some(x) if(request.getRequestURI.split("/")(1) == x.userName) => action
        case Some(x) => {
          val paths = request.getRequestURI.split("/")
          if(getCollaborators(paths(1), paths(2)).contains(x.userName)){
            action
          } else {
            redirect("/signin")
          }
        }
        case None => redirect("/signin")
      }
    }
  }

  protected def collaboratorsOnly[T](action: T => Any) = {
    (form: T) => {
      context.loginAccount match {
        case Some(x) if(x.userType == AccountService.Administrator) => action(form)
        case Some(x) if(request.getRequestURI.split("/")(1) == x.userName) => action(form)
        case Some(x) => {
          val paths = request.getRequestURI.split("/")
          if(getCollaborators(paths(1), paths(2)).contains(x.userName)){
            action(form)
          } else {
            redirect("/signin")
          }
        }
        case None => redirect("/signin")
      }
    }
  }
}