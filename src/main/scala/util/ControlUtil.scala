package util

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.DepthWalk.RevWalk

/**
 * Provides control facilities.
 */
object ControlUtil {

  def defining[A, B](value: A)(f: A => B): B = f(value)

  def using[A <% { def close(): Unit }, B](resource: A)(f: A => B): B =
    try {
      f(resource)
    } finally {
      if(resource != null){
        try {
          resource.close()
        } catch {
          case e: Throwable => // ignore
        }
      }
    }

  def using[T](git: Git)(f: Git => T): T =
    try {
      f(git)
    } finally {
      git.getRepository.close
    }

  def using[T](revWalk: RevWalk)(f: RevWalk => T): T =
    try {
      f(revWalk)
    } finally {
      revWalk.release()
    }

  def executeIf(condition: => Boolean)(action: => Unit): Boolean =
    if(condition){
      action
      true
    } else {
      false
    }

  def optionIf[T](condition: => Boolean)(action: => Option[T]): Option[T] =
    if(condition){
      action
    } else {
      None
    }
}
