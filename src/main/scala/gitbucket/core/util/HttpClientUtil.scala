package gitbucket.core.util

import java.net.{InetAddress, URL}

import gitbucket.core.service.SystemSettingsService
import org.apache.commons.net.util.SubnetUtils
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, CloseableHttpClient, HttpClientBuilder}

object HttpClientUtil {

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

    val httpClient = builder.build()

    try {
      f(httpClient)
    } finally {
      httpClient.close()
    }
  }

  def isPrivateAddress(address: String): Boolean = {
    val ipAddress = InetAddress.getByName(address)
    ipAddress.isSiteLocalAddress || ipAddress.isLinkLocalAddress || ipAddress.isLoopbackAddress
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
