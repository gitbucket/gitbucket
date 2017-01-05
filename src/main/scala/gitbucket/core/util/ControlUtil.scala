package gitbucket.core.util

import org.eclipse.jgit.api.Git
import scala.util.control.Exception._
import scala.language.reflectiveCalls

/**
 * Provides control facilities.
 */
object ControlUtil {

  def defining[A, B](value: A)(f: A => B): B = f(value)

  def using[A <% { def close(): Unit }, B](resource: A)(f: A => B): B =
    try f(resource) finally {
      if(resource != null){
        ignoring(classOf[Throwable]) {
          resource.close()
        }
      }
    }

  def using[A <% { def close(): Unit }, B <% { def close(): Unit }, C](resource1: A, resource2: B)(f: (A, B) => C): C =
    try f(resource1, resource2) finally {
      if(resource1 != null){
        ignoring(classOf[Throwable]) {
          resource1.close()
        }
      }
      if(resource2 != null){
        ignoring(classOf[Throwable]) {
          resource2.close()
        }
      }
    }

  def using[T](git: Git)(f: Git => T): T =
    try f(git) finally git.getRepository.close()

  def using[T](git1: Git, git2: Git)(f: (Git, Git) => T): T =
    try f(git1, git2) finally {
      git1.getRepository.close()
      git2.getRepository.close()
    }

  def ignore[T](f: => Unit): Unit = try {
    f
  } catch {
    case e: Exception => ()
  }

}
