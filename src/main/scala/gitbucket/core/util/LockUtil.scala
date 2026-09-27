package gitbucket.core.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.{ReentrantLock, Lock}

object LockUtil {

  /**
   * lock objects
   */
  private val locks = new ConcurrentHashMap[String, Lock]()

  /**
   * Returns the lock object for the specified repository.
   */
  private def getLockObject(key: String): Lock =
    locks.computeIfAbsent(key, _ => new ReentrantLock())

  /**
   * Synchronizes a given function which modifies the working copy of the wiki repository.
   */
  def lock[T](key: String)(f: => T): T = {
    val lock = getLockObject(key)
    try {
      lock.lock()
      f
    } finally {
      lock.unlock()
    }
  }

}
