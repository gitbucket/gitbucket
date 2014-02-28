package ssh

import org.apache.sshd.server.{CommandFactory, Environment, ExitCallback, Command}
import org.slf4j.LoggerFactory
import java.io.{InputStream, OutputStream}
import util.ControlUtil._
import org.eclipse.jgit.api.Git
import util.Directory._
import org.eclipse.jgit.transport.{ReceivePack, UploadPack}
import org.apache.sshd.server.command.UnknownCommand


class GitCommandFactory extends CommandFactory {
  private val logger = LoggerFactory.getLogger(classOf[GitCommandFactory])

  override def createCommand(command: String): Command = {
    logger.info(s"command: String -> " + command)
    command match {
      // TODO MUST use regular expression and UnitTest
      case s if s.startsWith("git-upload-pack") => new GitUploadPack(command)
      case s if s.startsWith("git-receive-pack") => new GitReceivePack(command)
      case _ => new UnknownCommand(command)
    }
  }
}

abstract class GitCommand(val command: String) extends Command {
  private val logger = LoggerFactory.getLogger(classOf[GitCommand])
  protected val (gitCommand, owner, repositoryName) = parseCommand
  protected var err: OutputStream = null
  protected var in: InputStream = null
  protected var out: OutputStream = null
  protected var callback: ExitCallback = null

  protected def runnable: Runnable

  override def start(env: Environment): Unit = {
    logger.info(s"start command : " + command)
    logger.info(s"parsed command : $gitCommand, $owner, $repositoryName")
    val thread = new Thread(runnable)
    thread.start()
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

  private def parseCommand: (String, String, String) = {
    // command sample: git-upload-pack '/username/repository_name.git'
    // command sample: git-receive-pack '/username/repository_name.git'
    // TODO This is not correct....
    val split = command.split(" ")
    val gitCommand = split(0)
    val gitUser = split(1).substring(1, split(1).length - 5).split("/")(1)
    val gitRepo = split(1).substring(1, split(1).length - 5).split("/")(2)
    (gitCommand, gitUser, gitRepo)
  }
}

class GitUploadPack(command: String) extends GitCommand(command: String) {
  override def runnable = new Runnable {
    override def run(): Unit = {
      using(Git.open(getRepositoryDir(owner, repositoryName))) { git =>
          val repository = git.getRepository
          val upload = new UploadPack(repository)
          upload.upload(in, out, err)
          callback.onExit(0)
      }
    }
  }
}

class GitReceivePack(command: String) extends GitCommand(command: String) {
  override def runnable = new Runnable {
    override def run(): Unit = {
      using(Git.open(getRepositoryDir(owner, repositoryName))) { git =>
          val repository = git.getRepository
          // TODO hook commit
          val receive = new ReceivePack(repository)
          receive.receive(in, out, err)
          callback.onExit(0)
      }
    }
  }
}
