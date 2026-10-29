package gitbucket.core.util

import gitbucket.core.service.SystemSettingsService.{WebHook, WebHookBlockPrivateAddress, WebHookWhitelist}
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap
import scala.util.Try

/**
 * Checks outgoing requests to user-supplied URLs (webhooks, clone from URL) against the private address settings,
 * and logs what happens to them. Everything is logged by this one logger, so it can be routed to its own log.
 */
object OutboundRequests {

  private val logger = LoggerFactory.getLogger("gitbucket.core.util.OutboundRequests")

  /** The same warning is logged at most once in this interval. */
  private val WarningIntervalMillis = 60 * 60 * 1000L
  private val MaxRememberedWarnings = 10000
  private val lastWarnings = new ConcurrentHashMap[String, java.lang.Long]()

  /** Thrown when a request is blocked; an IllegalArgumentException like the "Illegal address" error it replaces. */
  class BlockedAddressException(message: String) extends IllegalArgumentException(message)

  /**
   * Whether a request to the host is allowed. Requests to private addresses are logged: allowed ones because
   * blocking is off, and blocked ones. `target` describes the request, e.g. "webhook of repository root/test".
   */
  def isAllowed(settings: WebHook, host: String, target: => String): Boolean = {
    if (!settings.blockPrivateAddress) {
      // The client reports unknown hosts itself
      if (Try(HttpClientUtil.isPrivateAddress(host)).getOrElse(false)) {
        warnAtMostHourly(s"Allowed request to private address $host, blocking private addresses is off: $target")
      }
      true
    } else {
      lazy val whitelisted = settings.whitelist.exists(range => HttpClientUtil.inIpRange(range, host))
      val allowed = !HttpClientUtil.isPrivateAddress(host) || whitelisted
      if (!allowed) {
        warnAtMostHourly(s"Blocked request to private address $host: $target")
      }
      allowed
    }
  }

  /** Log the changes an admin made to the settings. */
  def logSettingsChange(before: WebHook, after: WebHook, userName: String): Unit = {
    def overridden(key: String): String =
      ConfigUtil.getConfigSource(key).map(source => s" (no effect, overridden by $source)").getOrElse("")

    if (before.blockPrivateAddress != after.blockPrivateAddress) {
      val message = s"$WebHookBlockPrivateAddress changed from ${before.blockPrivateAddress} to " +
        s"${after.blockPrivateAddress} by $userName${overridden(WebHookBlockPrivateAddress)}"
      if (after.blockPrivateAddress) logger.info(message) else logger.warn(message)
    }
    if (ranges(before) != ranges(after)) {
      logger.info(
        s"$WebHookWhitelist changed from ${format(ranges(before))} to ${format(ranges(after))} by $userName" +
          overridden(WebHookWhitelist)
      )
    }
  }

  /** Log the settings in effect, e.g. at startup. */
  def logSettings(settings: WebHook): Unit = {
    def setBy(key: String): String = ConfigUtil.getConfigSource(key).map(source => s" (set by $source)").getOrElse("")

    val requests = "Requests to private addresses (webhooks, clone from URL)"
    if (settings.blockPrivateAddress) {
      logger.info(
        s"$requests are blocked${setBy(WebHookBlockPrivateAddress)}, " +
          s"whitelist: ${format(ranges(settings))}${setBy(WebHookWhitelist)}"
      )
    } else {
      logger.warn(s"$requests are allowed${setBy(WebHookBlockPrivateAddress)}")
    }
  }

  private def ranges(settings: WebHook): Seq[String] = settings.whitelist.map(_.trim).filter(_.nonEmpty)

  private def format(ranges: Seq[String]): String = if (ranges.isEmpty) "none" else ranges.mkString(", ")

  private def warnAtMostHourly(message: String): Unit = {
    if (lastWarnings.size > MaxRememberedWarnings) {
      lastWarnings.clear()
    }
    val now: java.lang.Long = System.currentTimeMillis
    var log = false
    lastWarnings.compute(
      message,
      (_, last) =>
        if (last == null || now - last >= WarningIntervalMillis) {
          log = true
          now
        } else {
          last
        }
    )
    if (log) {
      logger.warn(message)
    }
  }
}
