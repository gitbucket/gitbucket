package ssh

import javax.servlet.{ServletContext, ServletContextEvent, ServletContextListener}
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.slf4j.LoggerFactory
import util.Directory
import service.SystemSettingsService
import java.util.concurrent.atomic.AtomicBoolean

object SshServer {
  private val logger = LoggerFactory.getLogger(SshServer.getClass)
  private val server = org.apache.sshd.SshServer.setUpDefaultServer()
  private val active = new AtomicBoolean(false)

  private def configure(context: ServletContext, port: Int) = {
    server.setPort(port)
    server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(s"${Directory.GitBucketHome}/gitbucket.ser"))
    server.setPublickeyAuthenticator(new PublicKeyAuthenticator(context))
    server.setCommandFactory(new GitCommandFactory(context))
    server.setShellFactory(new NoShell)
  }

  def start(context: ServletContext, port: Int) = {
    if(active.compareAndSet(false, true)){
      configure(context, port)
      server.start()
      logger.info(s"Start SSH Server Listen on ${server.getPort}")
    }
  }

  def stop() = {
    if(active.compareAndSet(true, false)){
      server.stop(true)
    }
  }

  def isActive = active.get
}

/*
 * Start a SSH Server Daemon
 *
 * How to use:
 * git clone ssh://username@host_or_ip:29418/owner/repository_name.git
 */
class SshServerListener extends ServletContextListener with SystemSettingsService {

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val settings = loadSystemSettings()
    if(settings.ssh){
      SshServer.start(sce.getServletContext, settings.sshPort.getOrElse(SystemSettingsService.DefaultSshPort))
    }
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    if(loadSystemSettings().ssh){
      SshServer.stop()
    }
  }

}
