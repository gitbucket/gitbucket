package ssh

import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener}
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.slf4j.LoggerFactory
import util.Directory


object SshServer {
  private val logger = LoggerFactory.getLogger(SshServer.getClass)

  val DEFAULT_PORT: Int = 29418
  // TODO read from config
  val SSH_SERVICE_ENABLE = true // TODO read from config

  private val server = org.apache.sshd.SshServer.setUpDefaultServer()

  // TODO think other way. this is for create database session
  private var context: ServletContext = null


  private def configure() = {
    server.setPort(DEFAULT_PORT) // TODO read from config
    server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(s"${Directory.GitBucketHome}/gitbucket.ser"))
    server.setPublickeyAuthenticator(new PublicKeyAuthenticator)
    server.setCommandFactory(new GitCommandFactory)
  }

  def start(context: ServletContext) = this.synchronized {
    if (SSH_SERVICE_ENABLE) {
      this.context = context
      configure()
      server.start()
      logger.info(s"Start SSH Server Listen on ${server.getPort}")
    }
  }

  def stop() = {
    server.stop(true)
  }

  def getServletContext = this.context
}

/*
 * Start a SSH Server Daemon
 *
 * How to use:
 * git clone ssh://username@host_or_ip:29418/owner/repository_name.git
 *
 */
class SshServerListener extends ServletContextListener {

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    SshServer.start(sce.getServletContext)
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    SshServer.stop()
  }

}
