package gitbucket.core.util

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import gitbucket.core.service.SystemSettingsService.WebHook
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*

class OutboundRequestsSpec extends AnyFunSuite {

  private val logger = LoggerFactory.getLogger("gitbucket.core.util.OutboundRequests").asInstanceOf[Logger]

  /** Run f and return its result with the messages logged meanwhile, as "LEVEL message". */
  private def logged[T](f: => T): (T, List[String]) = {
    val appender = new ListAppender[ILoggingEvent]()
    appender.start()
    logger.addAppender(appender)
    val result =
      try f
      finally logger.detachAppender(appender)
    (result, appender.list.asScala.map(e => s"${e.getLevel} ${e.getFormattedMessage}").toList)
  }

  private val blocking = WebHook(blockPrivateAddress = true, whitelist = Seq(""))
  private val notBlocking = WebHook(blockPrivateAddress = false, whitelist = Seq(""))

  test("isAllowed allows public addresses without logging") {
    assert(logged(OutboundRequests.isAllowed(blocking, "8.8.8.8", "public test")) == (true, Nil))
    assert(logged(OutboundRequests.isAllowed(notBlocking, "8.8.8.8", "public test")) == (true, Nil))
  }

  test("isAllowed blocks private addresses and logs a warning, at most once an hour") {
    val warning = "WARN Blocked request to private address 127.0.0.1: blocked test"
    assert(logged(OutboundRequests.isAllowed(blocking, "127.0.0.1", "blocked test")) == (false, List(warning)))
    assert(logged(OutboundRequests.isAllowed(blocking, "127.0.0.1", "blocked test")) == (false, Nil))
    assert(
      logged(OutboundRequests.isAllowed(blocking, "127.0.0.1", "blocked test 2")) ==
        (false, List(s"$warning 2"))
    )
  }

  test("isAllowed allows whitelisted private addresses without logging") {
    val whitelisted = blocking.copy(whitelist = Seq("127.0.0.0/8"))
    assert(logged(OutboundRequests.isAllowed(whitelisted, "127.0.0.1", "whitelisted test")) == (true, Nil))
  }

  test("isAllowed allows private addresses while blocking is off and logs a warning, at most once an hour") {
    val warning = "WARN Allowed request to private address 10.0.0.1, blocking private addresses is off: allowed test"
    assert(logged(OutboundRequests.isAllowed(notBlocking, "10.0.0.1", "allowed test")) == (true, List(warning)))
    assert(logged(OutboundRequests.isAllowed(notBlocking, "10.0.0.1", "allowed test")) == (true, Nil))
  }

  test("isAllowed leaves unknown hosts to the client while blocking is off") {
    assert(logged(OutboundRequests.isAllowed(notBlocking, "unknown.invalid", "unknown test")) == (true, Nil))
  }

  test("logSettingsChange logs what an admin changed") {
    assert(
      logged(OutboundRequests.logSettingsChange(blocking, notBlocking, "admin"))._2 ==
        List("WARN webhook.block_private_address changed from true to false by admin")
    )
    assert(
      logged(OutboundRequests.logSettingsChange(notBlocking, blocking, "admin"))._2 ==
        List("INFO webhook.block_private_address changed from false to true by admin")
    )
    val whitelisted = blocking.copy(whitelist = Seq("10.0.0.0/8 "))
    assert(
      logged(OutboundRequests.logSettingsChange(blocking, whitelisted, "admin"))._2 ==
        List("INFO webhook.whitelist changed from none to 10.0.0.0/8 by admin")
    )
    assert(logged(OutboundRequests.logSettingsChange(blocking, blocking.copy(whitelist = Nil), "admin"))._2 == Nil)
  }

  test("logSettingsChange and logSettings name a system property that overrides the setting") {
    System.setProperty("gitbucket.webhook.block_private_address", "false")
    try {
      val source = "system property gitbucket.webhook.block_private_address"
      val change = "webhook.block_private_address changed from false to true by admin"
      assert(
        logged(OutboundRequests.logSettingsChange(notBlocking, blocking, "admin"))._2 ==
          List(s"INFO $change (no effect, overridden by $source)")
      )
      assert(
        logged(OutboundRequests.logSettings(notBlocking))._2 ==
          List(s"WARN Requests to private addresses (webhooks, clone from URL) are allowed (set by $source)")
      )
    } finally {
      System.clearProperty("gitbucket.webhook.block_private_address")
    }
  }

  test("logSettings logs the settings in effect") {
    val whitelisted = blocking.copy(whitelist = Seq("10.0.0.0/8", "192.168.1.5"))
    assert(
      logged(OutboundRequests.logSettings(whitelisted))._2 == List(
        "INFO Requests to private addresses (webhooks, clone from URL) are blocked, whitelist: 10.0.0.0/8, 192.168.1.5"
      )
    )
  }
}
