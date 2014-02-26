package servlet

import javax.servlet.{ServletContextEvent, ServletContextListener}
import org.apache.sshd.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server._
import org.apache.sshd.server.session.ServerSession
import java.io.{OutputStream, InputStream}
import org.slf4j.LoggerFactory
import org.eclipse.jgit.transport.{ReceivePack, UploadPack}
import org.eclipse.jgit.api.Git
import util.Directory._
import util.ControlUtil._
import org.apache.sshd.server.command.UnknownCommand

/*
 * Start a SSH Service Daemon
 *
 * How to use ?
 * git clone ssh://username@host_or_ip:29418/username/repository_name.git
 *
 */
class SshServiceListener extends ServletContextListener {
  // TODO SshServer should be controllable by admin view (start and stop)
  // TODO Use Singleton object SshServer instance management
  private val logger = LoggerFactory.getLogger(classOf[SshServiceListener])

  val sshService = SshServer.setUpDefaultServer

  // TODO allow to disable ssh feature(ssh feature requires many stability test)
  val enableSsh = true

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    if (!enableSsh) return

    sshService.setPort(29418) // TODO must be configurable

    // TODO PasswordAuthentication should be disable
    // TODO Use PublicKeyAuthentication only => and It's stored db on account profile view
    val authenticator = new MyPasswordAuthenticator
    sshService.setPasswordAuthenticator(authenticator)

    // TODO gitbucket.ser should be in GITBUCKET_HOME
    sshService.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("gitbucket.ser"))

    sshService.setCommandFactory(new CommandFactory {
      override def createCommand(command: String): Command = {
        logger.info(s"command: String -> ${command}")
        command match {
          case s if s.startsWith("git-upload-pack ") => new GitUploadPack(command)
          case s if s.startsWith("git-receive-pack") => new GitReceivePack(command)
          case _ => new UnknownCommand(command)
        }
      }
    })
    sshService.start()
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    sshService.stop(true)
  }

}

// always true authenticator... TODO Implements PublicKeyAuthenticator
class MyPasswordAuthenticator extends PasswordAuthenticator {
  private val logger = LoggerFactory.getLogger(classOf[MyPasswordAuthenticator])

  override def authenticate(username: String, password: String, session: ServerSession): Boolean = {
    logger.info("noop authenticate!!!")
    true
  }
}

abstract class GitCommand(val command: String) extends Command {
  private val logger = LoggerFactory.getLogger(classOf[GitCommand])
  val (gitCommand, owner, repositoryName) = parseCommand
  var err: OutputStream = null
  var in: InputStream = null
  var out: OutputStream = null
  var callback: ExitCallback = null

  def runnable: Runnable

  override def start(env: Environment): Unit = {
    logger.info(s"start command : ${command}")
    logger.info(s"parsed command : ${gitCommand}, ${owner}, ${repositoryName}")
    val thread = new Thread(runnable)
    thread.start
  }

  override def destroy(): Unit = {
  }

  override def setExitCallback(callback: ExitCallback): Unit = {
    this.callback = callback
  }

  override def setErrorStream(err: OutputStream): Unit = {
    this.err = err
  }

  override def setOutputStream(out: OutputStream): Unit = {
    this.out = out
  }

  override def setInputStream(in: InputStream): Unit = {
    this.in = in
  }

  private def parseCommand: Tuple3[String, String, String] = {
    // command sample: git-upload-pack '/username/repository_name.git'
    // command sample: git-receive-pack '/username/repository_name.git'
    // TODO This is not correct....
    val splitted = command.split(" ")
    val gitCommand = splitted(0)
    val gitUser = splitted(1).substring(1, splitted(1).length - 5).split("/")(1)
    val gitRepo = splitted(1).substring(1, splitted(1).length - 5).split("/")(2)
    (gitCommand, gitUser, gitRepo)
  }
}
class GitUploadPack(command: String) extends GitCommand(command: String) {
  override val runnable = new Runnable {
    override def run(): Unit = {
      using(Git.open(getRepositoryDir(owner, repositoryName))) {
        git =>
          val repository = git.getRepository
          val upload = new UploadPack(repository)
          upload.upload(in, out, err)
          callback.onExit(0)
      }
    }
  }
}

class GitReceivePack(command: String) extends GitCommand(command: String) {
  override val runnable = new Runnable {
    override def run(): Unit = {
      using(Git.open(getRepositoryDir(owner, repositoryName))) {
        git =>
          val repository = git.getRepository
          val receive = new ReceivePack(repository)
          // TODO ReceivePack has many options. Need more check.
          receive.receive(in, out, err)
          callback.onExit(0)
      }
    }
  }
}

