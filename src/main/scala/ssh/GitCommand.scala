package ssh

import org.apache.sshd.server.{CommandFactory, Environment, ExitCallback, Command}
import org.slf4j.LoggerFactory
import java.io.{InputStream, OutputStream}
import util.ControlUtil._
import org.eclipse.jgit.api.Git
import util.Directory._
import org.eclipse.jgit.transport.{ReceivePack, UploadPack}
import org.apache.sshd.server.command.UnknownCommand
import servlet.{Database, CommitLogHook}
import service.{AccountService, RepositoryService, SystemSettingsService}
import org.eclipse.jgit.errors.RepositoryNotFoundException
import javax.servlet.ServletContext


object GitCommand {
  val CommandRegex = """\Agit-(upload|receive)-pack '/([a-zA-Z0-9\-_.]+)/([a-zA-Z0-9\-_.]+).git'\Z""".r
}

abstract class GitCommand(val context: ServletContext, val command: String) extends Command {
  self: RepositoryService with AccountService =>

  private val logger = LoggerFactory.getLogger(classOf[GitCommand])
  protected val (gitCommand, owner, repositoryName) = parseCommand
  protected var err: OutputStream = null
  protected var in: InputStream = null
  protected var out: OutputStream = null
  protected var callback: ExitCallback = null

  protected def runTask(user: String): Unit

  private def newTask(user: String): Runnable = new Runnable {
    override def run(): Unit = {
      Database(context) withTransaction {
        try {
          runTask(user)
          callback.onExit(0)
        } catch {
          case e: RepositoryNotFoundException =>
            logger.info(e.getMessage)
            callback.onExit(1, "Repository Not Found")
          case e: Throwable =>
            logger.error(e.getMessage, e)
            callback.onExit(1)
        }
      }
    }
  }

  override def start(env: Environment): Unit = {
    logger.info(s"start command : " + command)
    logger.info(s"parsed command : $gitCommand, $owner, $repositoryName")
    val user = env.getEnv.get("USER")
    val thread = new Thread(newTask(user))
    thread.start()
  }

  override def destroy(): Unit = {}

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
    // command sample: git-upload-pack '/owner/repository_name.git'
    // command sample: git-receive-pack '/owner/repository_name.git'
    // TODO This is not correct.... but works
    val split = command.split(" ")
    val gitCommand = split(0)
    val owner = split(1).substring(1, split(1).length - 5).split("/")(1)
    val repositoryName = split(1).substring(1, split(1).length - 5).split("/")(2)
    (gitCommand, owner, repositoryName)
  }

  protected def isWritableUser(username: String, repositoryInfo: RepositoryService.RepositoryInfo): Boolean =
    getAccountByUserName(username) match {
      case Some(account) => hasWritePermission(repositoryInfo.owner, repositoryInfo.name, Some(account))
      case None => false
    }

}

class GitUploadPack(context: ServletContext, command: String) extends GitCommand(context, command)
    with RepositoryService with AccountService {

  override protected def runTask(user: String): Unit = {
    getRepository(owner, repositoryName, null).foreach { repositoryInfo =>
      if(!repositoryInfo.repository.isPrivate || isWritableUser(user, repositoryInfo)){
        using(Git.open(getRepositoryDir(owner, repositoryName))) { git =>
          val repository = git.getRepository
          val upload = new UploadPack(repository)
          upload.upload(in, out, err)
        }
      }
    }
  }

}

class GitReceivePack(context: ServletContext, command: String) extends GitCommand(context, command)
    with SystemSettingsService with RepositoryService with AccountService {
  // TODO Correct this info. where i get base url?
  val BaseURL: String = loadSystemSettings().baseUrl.getOrElse("http://localhost:8080")

  override protected def runTask(user: String): Unit = {
    getRepository(owner, repositoryName, null).foreach { repositoryInfo =>
      if(isWritableUser(user, repositoryInfo)){
        using(Git.open(getRepositoryDir(owner, repositoryName))) { git =>
          val repository = git.getRepository
          val receive = new ReceivePack(repository)
          receive.setPostReceiveHook(new CommitLogHook(owner, repositoryName, user, BaseURL))
          receive.receive(in, out, err)
        }
      }
    }
  }

}

class GitCommandFactory(context: ServletContext) extends CommandFactory {
  private val logger = LoggerFactory.getLogger(classOf[GitCommandFactory])

  override def createCommand(command: String): Command = {
    logger.debug(s"command: $command")
    command match {
      case GitCommand.CommandRegex("upload", owner, repoName) => new GitUploadPack(context, command)
      case GitCommand.CommandRegex("receive", owner, repoName) => new GitReceivePack(context, command)
      case _ => new UnknownCommand(command)
    }
  }
}