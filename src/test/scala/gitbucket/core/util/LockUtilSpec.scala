package gitbucket.core.util

import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.scalatest.funsuite.AnyFunSuite

import scala.annotation.nowarn

class LockUtilSpec extends AnyFunSuite {

  // Only ever used as a safety net against a genuinely hung test. Every assertion that
  // matters is proven via a synchronization primitive.
  private val SafetyTimeoutMillis = 10000L

  private def await(latch: CountDownLatch): Unit =
    assert(
      latch.await(SafetyTimeoutMillis, TimeUnit.MILLISECONDS),
      "timed out waiting for a latch that should have been released"
    )

  /**
   * Spins (no sleeping) until `hasQueuedThread(thread)` reports `thread` queued waiting to
   * acquire a lock (works for both `ReentrantLock.hasQueuedThread` and
   * `ReentrantReadWriteLock.hasQueuedThread`). This is a deterministic proof that `thread` is
   * genuinely blocked, rather than merely not yet having run.
   */
  private def awaitQueued(thread: Thread)(hasQueuedThread: Thread => Boolean): Unit = {
    val deadline = System.nanoTime() + SafetyTimeoutMillis * 1000000L
    while (!hasQueuedThread(thread)) {
      if (System.nanoTime() > deadline) {
        fail(s"${thread.getName} never became queued")
      }
      Thread.onSpinWait()
    }
  }

  private def runInThread(name: String)(body: => Unit): Thread = {
    val thread = new Thread(
      new Runnable {
        override def run(): Unit = body
      },
      name
    )
    thread.setDaemon(true)
    thread.start()
    thread
  }

  @nowarn("cat=deprecation")
  private def callDeprecatedLock[T](key: String)(f: => T): T = LockUtil.lock(key)(f)

  private def uniqueUser(): String = s"user-${System.nanoTime()}-${scala.util.Random.nextInt()}"

  test("lockUser waits for an in-progress lockRepository on that user's repository to release") {
    val user = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val holderDone = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockRepository(user, "repo1") {
        holderAcquired.countDown()
        await(releaseGate)
      }
      holderDone.countDown()
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockUser(user) {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    // Proof of blocking: the contender is queued on the user's lock while the holder still
    // has not released it.
    awaitQueued(contender)(LockUtil.getUserLock(user).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(holderDone)
    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("lockRepository waits for an in-progress lockUser on that repository's user to release") {
    val user = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val holderDone = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockUser(user) {
        holderAcquired.countDown()
        await(releaseGate)
      }
      holderDone.countDown()
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockRepository(user, "repo1") {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getUserLock(user).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(holderDone)
    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("lockRepository serializes two calls for the same user/repository") {
    val user = uniqueUser()
    val repo = "repo1"
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockRepository(user, repo) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockRepository(user, repo) {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getRepoLock(s"$user/$repo").hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("lockRepository does not serialize calls for two different repositories of the same user") {
    val user = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val secondAcquired = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockRepository(user, "repo1") {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val second = runInThread("lock-util-spec-second") {
      LockUtil.lockRepository(user, "repo2") {
        secondAcquired.countDown()
      }
    }

    // If lockRepository incorrectly serialized different repositories of the same user, this
    // would time out, since the holder is deliberately not releasing yet.
    await(secondAcquired)

    releaseGate.countDown()
    holder.join(SafetyTimeoutMillis)
    second.join(SafetyTimeoutMillis)
  }

  test("lockWiki does not contend with lockRepository for the same user/repository") {
    val user = uniqueUser()
    val repo = "repo1"
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val wikiAcquired = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockRepository(user, repo) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val wiki = runInThread("lock-util-spec-wiki") {
      LockUtil.lockWiki(user, repo) {
        wikiAcquired.countDown()
      }
    }

    await(wikiAcquired)

    releaseGate.countDown()
    holder.join(SafetyTimeoutMillis)
    wiki.join(SafetyTimeoutMillis)
  }

  test("lockWiki serializes two calls for the same user/repository") {
    val user = uniqueUser()
    val repo = "repo1"
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockWiki(user, repo) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockWiki(user, repo) {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getRepoLock(s"$user/$repo/wiki").hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("deprecated lock still serializes two calls for the same key") {
    val key = s"legacy-${uniqueUser()}"
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      callDeprecatedLock(key) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      callDeprecatedLock(key) {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getRepoLock(key).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("deprecated lock and lockRepository contend on the same key, proving the key format matches") {
    val user = uniqueUser()
    val repo = "repo1"
    val key = s"$user/$repo"
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      callDeprecatedLock(key) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockRepository(user, repo) {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getRepoLock(key).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("deprecated lock waits for an in-progress lockUser on that key's user to release") {
    val user = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockUser(user) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      callDeprecatedLock(s"$user/repo1") {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getUserLock(user).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("lockUser waits for an in-progress deprecated lock on that user's key to release") {
    val user = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    // The username is everything up to (excluding) the first '/', so this key's user is
    // "user", not "user/repo1".
    val holder = runInThread("lock-util-spec-holder") {
      callDeprecatedLock(s"$user/repo1/wiki") {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockUser(user) {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getUserLock(user).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("lockUserForRead waits for an in-progress lockUser on that user to release") {
    val user = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockUser(user) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockUserForRead(user) {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getUserLock(user).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("lockUser waits for an in-progress lockUserForRead on that user to release") {
    val user = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockUserForRead(user) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockUser(user) {
        contenderAcquired.countDown()
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getUserLock(user).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

  test("lockUserForRead does not serialize two concurrent calls for the same user") {
    val user = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val secondAcquired = new CountDownLatch(1)

    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockUserForRead(user) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    val second = runInThread("lock-util-spec-second") {
      LockUtil.lockUserForRead(user) {
        secondAcquired.countDown()
      }
    }

    // If lockUserForRead incorrectly excluded another reader, this would time out, since the
    // holder is deliberately not releasing yet.
    await(secondAcquired)

    releaseGate.countDown()
    holder.join(SafetyTimeoutMillis)
    second.join(SafetyTimeoutMillis)
  }

  test(
    "a repository transfer (lockRepository on the source nested in lockUserForRead on the destination) " +
      "waits for an in-progress lockUser on the destination user to release"
  ) {
    val sourceUser = uniqueUser()
    val destinationUser = uniqueUser()
    val holderAcquired = new CountDownLatch(1)
    val releaseGate = new CountDownLatch(1)
    val contenderAcquired = new CountDownLatch(1)
    val contenderDone = new CountDownLatch(1)

    // Simulates a concurrent rename of the transfer's destination user.
    val holder = runInThread("lock-util-spec-holder") {
      LockUtil.lockUser(destinationUser) {
        holderAcquired.countDown()
        await(releaseGate)
      }
    }

    await(holderAcquired)

    // Simulates RepositoryService.renameRepository transferring a repository from
    // sourceUser to destinationUser.
    val contender = runInThread("lock-util-spec-contender") {
      LockUtil.lockUserForRead(destinationUser) {
        LockUtil.lockRepository(sourceUser, "repo1") {
          contenderAcquired.countDown()
        }
      }
      contenderDone.countDown()
    }

    awaitQueued(contender)(LockUtil.getUserLock(destinationUser).hasQueuedThread)
    assert(contenderAcquired.getCount == 1)

    releaseGate.countDown()

    await(contenderAcquired)
    await(contenderDone)

    holder.join(SafetyTimeoutMillis)
    contender.join(SafetyTimeoutMillis)
  }

}
