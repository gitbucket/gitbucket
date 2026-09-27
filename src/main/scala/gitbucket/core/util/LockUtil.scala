package gitbucket.core.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.{ReentrantLock, ReentrantReadWriteLock}

/**
 * A collection of scoped write locks. The more granular locks (repo and wiki) also
 * take a read lock on their parent (the user/owner).
 */
object LockUtil {

  /**
   * Keyed by username.
   */
  private[gitbucket] val userLocks = new ConcurrentHashMap[String, ReentrantReadWriteLock]()

  /**
   * Keyed by the same string historically passed to the now-deprecated [[lock]] method
   * (e.g. "owner/repo" or "owner/repo/wiki"). Always taken exclusively.
   */
  private[gitbucket] val repoLocks = new ConcurrentHashMap[String, ReentrantLock]()

  private[gitbucket] def getUserLock(user: String): ReentrantReadWriteLock =
    userLocks.computeIfAbsent(user, _ => new ReentrantReadWriteLock(true))

  private[gitbucket] def getRepoLock(key: String): ReentrantLock =
    repoLocks.computeIfAbsent(key, _ => new ReentrantLock())

  /**
   * Lock a repository for read or write. Also takes a read lock on the user, assumed to
   * be the portion of `key` before the first '/'.
   *
   * @deprecated use [[lockRepository]] instead (or [[lockWiki]] for a wiki repository)
   */
  @deprecated("use lockRepository or lockWiki instead", "4.49.0")
  def lock[T](key: String)(f: => T): T =
    withRepositoryLock(key.takeWhile(_ != '/'), key)(f)

  /**
   * Lock a repository for read or write. Does not lock any corresponding wiki, which is
   * considered a unique repository.
   *
   * Also takes a read lock on the user who owns the repository.
   */
  private[gitbucket] def lockRepository[T](user: String, repo: String)(f: => T): T =
    withRepositoryLock(user, s"$user/$repo")(f)

  /**
   * Lock a wiki repository for read or write. Does not lock any corresponding code repo,
   * which is considered a unique repository.
   *
   * Also takes a read lock on the user who owns the wiki.
   */
  private[gitbucket] def lockWiki[T](user: String, repo: String)(f: => T): T =
    withRepositoryLock(user, s"$user/$repo/wiki")(f)

  private def withRepositoryLock[T](user: String, repoKey: String)(f: => T): T =
    lockUserForRead(user) {
      val repoLock = getRepoLock(repoKey)
      repoLock.lock()
      try {
        f
      } finally {
        repoLock.unlock()
      }
    }

  /**
   * Lock a user for read. Does not take any repository-level lock of its own. Used to block
   * a concurrent [[lockUser]] (e.g. a rename) on `user`, without excluding other repository
   * operations on `user`'s repositories - for example, while moving a repository into
   * `user`'s directory tree as part of a transfer to `user`.
   */
  private[gitbucket] def lockUserForRead[T](user: String)(f: => T): T = {
    val userReadLock = getUserLock(user).readLock()
    userReadLock.lock()
    try {
      f
    } finally {
      userReadLock.unlock()
    }
  }

  /**
   * Lock a user for read or write. Blocks read or write on any wiki or repo owned
   * by the user, since those operations first acquire a read lock on the user.
   */
  private[gitbucket] def lockUser[T](user: String)(f: => T): T = {
    val userWriteLock = getUserLock(user).writeLock()
    userWriteLock.lock()
    try {
      f
    } finally {
      userWriteLock.unlock()
    }
  }

}
