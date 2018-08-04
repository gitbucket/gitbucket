package gitbucket.core.util

import gitbucket.core.service.SystemSettingsService
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

}
