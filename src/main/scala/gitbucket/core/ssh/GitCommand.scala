package gitbucket.core.ssh

import gitbucket.core.model.Session
import gitbucket.core.plugin.{GitRepositoryRouting, PluginRegistry}
import gitbucket.core.service.{RepositoryService, AccountService, SystemSettingsService}
import gitbucket.core.servlet.{Database, CommitLogHook}
import gitbucket.core.util.{Directory, ControlUtil}
import org.apache.sshd.server.{CommandFactory, Environment, ExitCallback, Command}
import org.slf4j.LoggerFactory
import java.io.{File, InputStream, OutputStream}
import ControlUtil._
import org.eclipse.jgit.api.Git
import Directory._
import org.eclipse.jgit.transport.{ReceivePack, UploadPack}
import org.apache.sshd.server.command.UnknownCommand
import org.eclipse.jgit.errors.RepositoryNotFoundException

object GitCommand {
  val DefaultCommandRegex = """\Agit-(upload|receive)-pack '/([a-zA-Z0-9\-_.]+)/([a-zA-Z0-9\-_.]+).git'\Z""".r
  val SimpleCommandRegex  = """\Agit-(upload|receive)-pack '/(.+\.git)'\Z""".r
}

abstract class GitCommand() extends Command {

  private val logger = LoggerFactory.getLogger(classOf[GitCommand])
  protected var err: OutputStream = null
  protected var in: InputStream = null
  protected var out: OutputStream = null
  protected var callback: ExitCallback = null

  protected def runTask(user: String)(implicit session: Session): Unit

  private def newTask(user: String): Runnable = new Runnable {
    override def run(): Unit = {
      Database() withSession { implicit session =>
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

}

abstract class DefaultGitCommand(val owner: String, val repoName: String) extends GitCommand {
  self: RepositoryService with AccountService =>

  protected def isWritableUser(username: String, repositoryInfo: RepositoryService.RepositoryInfo)
                              (implicit session: Session): Boolean =
    getAccountByUserName(username) match {
      case Some(account) => hasWritePermission(repositoryInfo.owner, repositoryInfo.name, Some(account))
      case None => false
    }

}


class DefaultGitUploadPack(owner: String, repoName: String, baseUrl: String) extends DefaultGitCommand(owner, repoName)
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

class DefaultGitReceivePack(owner: String, repoName: String, baseUrl: String) extends DefaultGitCommand(owner, repoName)
    with RepositoryService with AccountService {

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

class PluginGitUploadPack(repoName: String, baseUrl: String, routing: GitRepositoryRouting) extends GitCommand
    with SystemSettingsService {

  override protected def runTask(user: String)(implicit session: Session): Unit = {
    if(routing.filter.filter("/" + repoName, Some(user), loadSystemSettings(), false)){
      val path = routing.urlPattern.r.replaceFirstIn(repoName, routing.localPath)
      using(Git.open(new File(Directory.GitBucketHome, path))){ git =>
        val repository = git.getRepository
        val upload = new UploadPack(repository)
        upload.upload(in, out, err)
      }
    }
  }
}

class PluginGitReceivePack(repoName: String, baseUrl: String, routing: GitRepositoryRouting) extends GitCommand
    with SystemSettingsService {

  override protected def runTask(user: String)(implicit session: Session): Unit = {
    if(routing.filter.filter("/" + repoName, Some(user), loadSystemSettings(), true)){
      val path = routing.urlPattern.r.replaceFirstIn(repoName, routing.localPath)
      using(Git.open(new File(Directory.GitBucketHome, path))){ git =>
        val repository = git.getRepository
        val receive = new ReceivePack(repository)
        receive.receive(in, out, err)
      }
    }
  }
}


class GitCommandFactory(baseUrl: String) extends CommandFactory {
  private val logger = LoggerFactory.getLogger(classOf[GitCommandFactory])

  override def createCommand(command: String): Command = {
    import GitCommand._
    logger.debug(s"command: $command")

    command match {
      case SimpleCommandRegex ("upload" , repoName) if(pluginRepository(repoName)) => new PluginGitUploadPack (repoName, baseUrl, routing(repoName))
      case SimpleCommandRegex ("receive", repoName) if(pluginRepository(repoName)) => new PluginGitReceivePack(repoName, baseUrl, routing(repoName))
      case DefaultCommandRegex("upload" , owner, repoName) => new DefaultGitUploadPack (owner, repoName, baseUrl)
      case DefaultCommandRegex("receive", owner, repoName) => new DefaultGitReceivePack(owner, repoName, baseUrl)
      case _ => new UnknownCommand(command)
    }
  }

  private def pluginRepository(repoName: String): Boolean = PluginRegistry().getRepositoryRouting("/" + repoName).isDefined
  private def routing(repoName: String): GitRepositoryRouting = PluginRegistry().getRepositoryRouting("/" + repoName).get

}
