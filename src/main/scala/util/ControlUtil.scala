package util

import org.eclipse.jgit.api.Git

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

  /**
   * Use this method to use the Git object.
   * Repository resources are released certainly after processing.
   */
  def using[T](git: Git)(f: Git => T): T =
    try {
      f(git)
    } finally {
      git.getRepository.close
    }


}
