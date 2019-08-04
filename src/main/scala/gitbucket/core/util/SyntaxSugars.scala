package gitbucket.core.util

import org.eclipse.jgit.api.Git
import scala.util.control.Exception._
import scala.language.reflectiveCalls

/**
 * Provides control facilities.
 */
object SyntaxSugars {

  def defining[A, B](value: A)(f: A => B): B = f(value)

  @deprecated("Use scala.util.Using.resource instead", "4.32.0")
  def using[A <: { def close(): Unit }, B](resource: A)(f: A => B): B =
    try f(resource)
    finally {
      if (resource != null) {
        ignoring(classOf[Throwable]) {
          resource.close()
        }
      }
    }

  @deprecated("Use scala.util.Using.resources instead", "4.32.0")
  def using[A <: { def close(): Unit }, B <: { def close(): Unit }, C](resource1: A, resource2: B)(f: (A, B) => C): C =
    try f(resource1, resource2)
    finally {
      if (resource1 != null) {
        ignoring(classOf[Throwable]) {
          resource1.close()
        }
      }
      if (resource2 != null) {
        ignoring(classOf[Throwable]) {
          resource2.close()
        }
      }
    }

  @deprecated("Use scala.util.Using.resource instead", "4.32.0")
  def using[T](git: Git)(f: Git => T): T =
    try f(git)
    finally git.getRepository.close()

  @deprecated("Use scala.util.Using.resources instead", "4.32.0")
  def using[T](git1: Git, git2: Git)(f: (Git, Git) => T): T =
    try f(git1, git2)
    finally {
      git1.getRepository.close()
      git2.getRepository.close()
    }

  def ignore[T](f: => Unit): Unit =
    try {
      f
    } catch {
      case _: Exception => ()
    }

  object ~ {
    def unapply[A, B](t: (A, B)): Option[(A, B)] = Some(t)
  }

  /**
   * Provides easier and explicit ways to access to a head value of `Map[String, Seq[String]]`.
   * This is intended to use in implementations of scalatra-forms's `Constraint` or `ValueType`.
   */
  implicit class HeadValueAccessibleMap(map: Map[String, Seq[String]]) {
    def value(key: String): String = map(key).head
    def optionValue(key: String): Option[String] = map.get(key).flatMap(_.headOption)
    def values(key: String): Seq[String] = map.getOrElse(key, Seq.empty)
  }

}
