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
import model.Session

object GitCommand {
  val CommandRegex = """\Agit-(upload|receive)-pack '/([a-zA-Z0-9\-_.]+)/([a-zA-Z0-9\-_.]+).git'\Z""".r
}

abstract class GitCommand(val context: ServletContext, val owner: String, val repoName: String) extends Command {
  self: RepositoryService with AccountService =>

  private val logger = LoggerFactory.getLogger(classOf[GitCommand])
  protected var err: OutputStream = null
  protected var in: InputStream = null
  protected var out: OutputStream = null
  protected var callback: ExitCallback = null

  protected def runTask(user: String)(implicit session: Session): Unit

  private def newTask(user: String): Runnable = new Runnable {
    override def run(): Unit = {
      Database(context) withSession { implicit session =>
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

  protected def isWritableUser(username: String, repositoryInfo: RepositoryService.RepositoryInfo)
                              (implicit session: Session): Boolean =
    getAccountByUserName(username) match {
      case Some(account) => hasWritePermission(repositoryInfo.owner, repositoryInfo.name, Some(account))
      case None => false
    }

}

class GitUploadPack(context: ServletContext, owner: String, repoName: String, baseUrl: String) extends GitCommand(context, owner, repoName)
    with RepositoryService with AccountService {

  override protected def runTask(user: String)(implicit session: Session): Unit = {
    getRepository(owner, repoName.replaceFirst("\\.wiki\\Z", ""), baseUrl).foreach { repositoryInfo =>
      if(!repositoryInfo.repository.isPrivate || isWritableUser(user, repositoryInfo)){
        using(Git.open(getRepositoryDir(owner, repoName))) { git =>
          val repository = git.getRepository
          val upload = new UploadPack(repository)
          upload.upload(in, out, err)
        }
      }
    }
  }

}

class GitReceivePack(context: ServletContext, owner: String, repoName: String, baseUrl: String) extends GitCommand(context, owner, repoName)
    with SystemSettingsService with RepositoryService with AccountService {

  override protected def runTask(user: String)(implicit session: Session): Unit = {
    getRepository(owner, repoName.replaceFirst("\\.wiki\\Z", ""), baseUrl).foreach { repositoryInfo =>
      if(isWritableUser(user, repositoryInfo)){
        using(Git.open(getRepositoryDir(owner, repoName))) { git =>
          val repository = git.getRepository
          val receive = new ReceivePack(repository)
          if(!repoName.endsWith(".wiki")){
            val hook = new CommitLogHook(owner, repoName, user, baseUrl)
            receive.setPreReceiveHook(hook)
            receive.setPostReceiveHook(hook)
          }
          receive.receive(in, out, err)
        }
      }
    }
  }

}

class GitCommandFactory(context: ServletContext, baseUrl: String) extends CommandFactory {
  private val logger = LoggerFactory.getLogger(classOf[GitCommandFactory])

  override def createCommand(command: String): Command = {
    logger.debug(s"command: $command")
    command match {
      case GitCommand.CommandRegex("upload", owner, repoName) => new GitUploadPack(context, owner, repoName, baseUrl)
      case GitCommand.CommandRegex("receive", owner, repoName) => new GitReceivePack(context, owner, repoName, baseUrl)
      case _ => new UnknownCommand(command)
    }
  }
}