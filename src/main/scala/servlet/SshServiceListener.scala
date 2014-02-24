package servlet

import javax.servlet.{ServletContextEvent, ServletContextListener}
import org.apache.sshd.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server._
import org.apache.sshd.server.session.ServerSession
import java.io.{OutputStream, InputStream}
import org.slf4j.LoggerFactory

/*
 * Start a SSH Service Daemon
 */
class SshServiceListener extends ServletContextListener {
  private val logger = LoggerFactory.getLogger(classOf[SshServiceListener])

  val sshService = SshServer.setUpDefaultServer
  // TODO make configurable ssh feature
  val enableSsh = true

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    if (!enableSsh) return
    sshService.setPort(29418)

    val authenticator = new MyPasswordAuthenticator
    sshService.setPasswordAuthenticator(authenticator)
    // TODO gitbucket.ser should be in GITBUCKET_HOME
    sshService.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("gitbucket.ser"))
    sshService.setCommandFactory(new CommandFactory{
      override def createCommand(command: String): Command = {
        logger.error("createCommand!")
        return new Command{
          override def destroy(): Unit = {}

          override def start(env: Environment): Unit = {
            logger.info("start command")
            logger.info(env.getEnv.toString)
          }

          override def setExitCallback(callback: ExitCallback): Unit = {}

          override def setErrorStream(err: OutputStream): Unit = {}

          override def setOutputStream(out: OutputStream): Unit = {}

          override def setInputStream(in: InputStream): Unit = {}
        }
      }
    })
    sshService.start()
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    sshService.stop(true)
  }

}

class MyPasswordAuthenticator extends PasswordAuthenticator {
  private val logger = LoggerFactory.getLogger(classOf[MyPasswordAuthenticator])

  override def authenticate(username: String, password: String, session: ServerSession): Boolean = {
    logger.error("authenticate!!!")
    true
  }
}
