package gitbucket.core

import java.net.InetSocketAddress
import java.nio.file.Files
import java.io.File

import gitbucket.core.util.{FileUtil, HttpClientUtil}
import org.apache.http.client.methods.HttpGet
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.server.{Handler, Server}
import org.eclipse.jetty.webapp.WebAppContext
import org.kohsuke.github.GitHub

class TestingGitBucketServer(val port: Int = 19999) extends AutoCloseable {
  private var server: Server = null
  private var dir: File = null

  start()

  private def start(): Unit = {
    System.setProperty("java.awt.headless", "true")

    dir = Files.createTempDirectory("gitbucket-test-").toFile
    System.setProperty("gitbucket.home", dir.getAbsolutePath)

    val address = new InetSocketAddress(port)
    server = new Server(address)

    val context = new WebAppContext
    context.setResourceBase("./target/webapp")
    context.setContextPath("")
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    context.setServer(server)

    val handler = addStatisticsHandler(context)
    server.setHandler(handler)

    server.start()

    HttpClientUtil.withHttpClient(None) { httpClient =>
      var launched = false
      var count = 0
      while (!launched && count < 10) {
        Thread.sleep(500)
        val res = httpClient.execute(new HttpGet(s"http://localhost:${port}/"))
        launched = res.getStatusLine.getStatusCode == 200
        count += 1
      }
    }
  }

  def client(login: String, password: String): GitHub =
    GitHub.connectToEnterprise(s"http://localhost:${port}/api/v3", login, password)

  private def addStatisticsHandler(handler: Handler) = { // The graceful shutdown is implemented via the statistics handler.
    // See the following: https://bugs.eclipse.org/bugs/show_bug.cgi?id=420142
    val statisticsHandler = new StatisticsHandler
    statisticsHandler.setHandler(handler)
    statisticsHandler
  }

  def close(): Unit = {
    server.stop()
    FileUtil.deleteIfExists(dir)
  }
}
