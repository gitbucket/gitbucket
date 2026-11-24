package gitbucket.core.util

import java.net.{Inet4Address, InetAddress}

import gitbucket.core.service.SystemSettingsService
import org.apache.commons.net.util.SubnetUtils
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, CloseableHttpClient, HttpClientBuilder}
import scala.util.Using

object HttpClientUtil {

  /** 100.64.0.0/10, the shared address space of RFC 6598 (carrier-grade NAT). */
  private val sharedAddressSpace = {
    val utils = new SubnetUtils("100.64.0.0/10")
    utils.setInclusiveHostCount(true)
    utils.getInfo
  }

  def withHttpClient[T](proxy: Option[SystemSettingsService.Proxy])(f: CloseableHttpClient => T): T = {
    val builder = HttpClientBuilder.create.useSystemProperties

    proxy.foreach { proxy =>
      builder.setProxy(new HttpHost(proxy.host, proxy.port))

      for (user <- proxy.user; password <- proxy.password) {
        val credential = new BasicCredentialsProvider()
        credential.setCredentials(
          new AuthScope(proxy.host, proxy.port),
          new UsernamePasswordCredentials(user, password)
        )
        builder.setDefaultCredentialsProvider(credential)
      }
    }

    Using.resource(builder.build())(f)
  }

  def isPrivateAddress(address: String): Boolean = {
    val ipAddress = InetAddress.getByName(address)
    ipAddress.isAnyLocalAddress ||
    ipAddress.isSiteLocalAddress ||
    ipAddress.isLinkLocalAddress ||
    ipAddress.isLoopbackAddress ||
    isSharedAddressSpace(ipAddress)
  }

  private def isSharedAddressSpace(ipAddress: InetAddress): Boolean = ipAddress match {
    case ipv4: Inet4Address => sharedAddressSpace.isInRange(ipv4.getHostAddress)
    case _                  => false
  }

  def inIpRange(ipRange: String, ipAddress: String): Boolean = {
    if (ipRange.contains('/')) {
      val utils = new SubnetUtils(ipRange)
      utils.setInclusiveHostCount(true)
      utils.getInfo.isInRange(ipAddress)
    } else {
      ipRange == ipAddress
    }
  }

}
