package util

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk

/**
 * Provides control facilities.
 */
object ControlUtil {

  def defining[A, B](value: A)(f: A => B): B = f(value)

  def using[A <% { def close(): Unit }, B](resource: A)(f: A => B): B =
    try f(resource) finally {
      if(resource != null){
        try {
          resource.close()
        } catch {
          case e: Throwable => // ignore
        }
      }
    }

  def using[T](git: Git)(f: Git => T): T =
    try f(git) finally git.getRepository.close

  def using[T](git1: Git, git2: Git)(f: (Git, Git) => T): T =
    try f(git1, git2) finally {
      git1.getRepository.close
      git2.getRepository.close
    }

  def using[T](revWalk: RevWalk)(f: RevWalk => T): T =
    try f(revWalk) finally revWalk.release()

  def using[T](treeWalk: TreeWalk)(f: TreeWalk => T): T =
    try f(treeWalk) finally treeWalk.release()

  def executeIf(condition: => Boolean)(action: => Unit): Boolean =
    if(condition){
      action
      true
    } else false

  def optionIf[T](condition: => Boolean)(action: => Option[T]): Option[T] =
    if(condition) action else None
}
