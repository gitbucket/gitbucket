package gitbucket.core.ssh

import java.util.concurrent.atomic.AtomicReference
import javax.servlet.{ServletContextEvent, ServletContextListener}
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.service.SystemSettingsService.SshAddress
import gitbucket.core.util.Directory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.slf4j.LoggerFactory

object SshServer {
  private val logger = LoggerFactory.getLogger(SshServer.getClass)
  private val server = new AtomicReference[org.apache.sshd.server.SshServer](null)

  private def configure(
    bindAddress: SshAddress,
    publicAddress: SshAddress,
    baseUrl: String
  ): org.apache.sshd.server.SshServer = {
    val server = org.apache.sshd.server.SshServer.setUpDefaultServer()
    server.setPort(bindAddress.port)
    val provider = new SimpleGeneratorHostKeyProvider(
      java.nio.file.Paths.get(s"${Directory.GitBucketHome}/gitbucket.ser")
    )
    provider.setAlgorithm("RSA")
    provider.setOverwriteAllowed(false)
    server.setKeyPairProvider(provider)
    server.setPublickeyAuthenticator(new PublicKeyAuthenticator(bindAddress.genericUser))
    server.setCommandFactory(
      new GitCommandFactory(baseUrl, publicAddress)
    )
    server.setShellFactory(new NoShell(publicAddress))
    server
  }

  def start(bindAddress: SshAddress, publicAddress: SshAddress, baseUrl: String): Unit = {
    this.server.synchronized {
      val server = configure(bindAddress, publicAddress, baseUrl)
      if (this.server.compareAndSet(null, server)) {
        server.start()
        logger.info(s"Start SSH Server Listen on ${server.getPort}")
      }
    }
  }

  def stop(): Unit = {
    this.server.synchronized {
      val server = this.server.getAndSet(null)
      if (server != null) {
        server.stop()
        logger.info("SSH Server is stopped.")
      }
    }
  }
}

/*
 * Start a SSH Server Daemon
 *
 * How to use:
 * git clone ssh://username@host_or_ip:29418/owner/repository_name.git
 */
class SshServerListener extends ServletContextListener with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[SshServerListener])

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val settings = loadSystemSettings()
    if (settings.sshBindAddress.isDefined && settings.baseUrl.isEmpty) {
      logger.error("Could not start SshServer because the baseUrl is not configured.")
    }
    for {
      bindAddress <- settings.sshBindAddress
      publicAddress <- settings.sshPublicAddress
      baseUrl <- settings.baseUrl
    } SshServer.start(bindAddress, publicAddress, baseUrl)
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    SshServer.stop()
  }

}
