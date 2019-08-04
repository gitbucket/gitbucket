package gitbucket.core.ssh

import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.plugin.{GitRepositoryRouting, PluginRegistry}
import gitbucket.core.service.{AccountService, DeployKeyService, RepositoryService, SystemSettingsService}
import gitbucket.core.servlet.{CommitLogHook, Database}
import gitbucket.core.util.{SyntaxSugars, Directory}
import org.apache.sshd.server.{Environment, ExitCallback, SessionAware}
import org.apache.sshd.server.command.{Command, CommandFactory}
import org.apache.sshd.server.session.ServerSession
import org.slf4j.LoggerFactory
import java.io.{File, InputStream, OutputStream}

import org.eclipse.jgit.api.Git
import Directory._
import gitbucket.core.ssh.PublicKeyAuthenticator.AuthType
import org.eclipse.jgit.transport.{ReceivePack, UploadPack}
import org.apache.sshd.server.shell.UnknownCommand
import org.eclipse.jgit.errors.RepositoryNotFoundException
import scala.util.Using

object GitCommand {
  val DefaultCommandRegex = """\Agit-(upload|receive)-pack '/([a-zA-Z0-9\-_.]+)/([a-zA-Z0-9\-\+_.]+).git'\Z""".r
  val SimpleCommandRegex = """\Agit-(upload|receive)-pack '/(.+\.git)'\Z""".r
}

abstract class GitCommand extends Command with SessionAware {

  private val logger = LoggerFactory.getLogger(classOf[GitCommand])

  @volatile protected var err: OutputStream = null
  @volatile protected var in: InputStream = null
  @volatile protected var out: OutputStream = null
  @volatile protected var callback: ExitCallback = null
  @volatile private var authType: Option[AuthType] = None

  protected def runTask(authType: AuthType): Unit

  private def newTask(): Runnable = () => {
    authType match {
      case Some(authType) =>
        try {
          runTask(authType)
          callback.onExit(0)
        } catch {
          case e: RepositoryNotFoundException =>
            logger.info(e.getMessage)
            callback.onExit(1, "Repository Not Found")
          case e: Throwable =>
            logger.error(e.getMessage, e)
            callback.onExit(1)
        }
      case None =>
        val message = "User not authenticated"
        logger.error(message)
        callback.onExit(1, message)
    }
  }

  final override def start(env: Environment): Unit = {
    val thread = new Thread(newTask())
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

  override def setSession(serverSession: ServerSession): Unit = {
    this.authType = PublicKeyAuthenticator.getAuthType(serverSession)
  }

}

abstract class DefaultGitCommand(val owner: String, val repoName: String) extends GitCommand {
  self: RepositoryService with AccountService with DeployKeyService =>

  protected def userName(authType: AuthType): String = {
    authType match {
      case AuthType.UserAuthType(userName) => userName
      case AuthType.DeployKeyType(_)       => owner
    }
  }

  protected def isReadableUser(authType: AuthType, repositoryInfo: RepositoryService.RepositoryInfo)(
    implicit session: Session
  ): Boolean = {
    authType match {
      case AuthType.UserAuthType(username) => {
        getAccountByUserName(username) match {
          case Some(account) => hasGuestRole(owner, repoName, Some(account))
          case None          => false
        }
      }
      case AuthType.DeployKeyType(key) => {
        getDeployKeys(owner, repoName).filter(sshKey => SshUtil.str2PublicKey(sshKey.publicKey).contains(key)) match {
          case List(_) => true
          case _       => false
        }
      }
    }
  }

  protected def isWritableUser(authType: AuthType, repositoryInfo: RepositoryService.RepositoryInfo)(
    implicit session: Session
  ): Boolean = {
    authType match {
      case AuthType.UserAuthType(username) => {
        getAccountByUserName(username) match {
          case Some(account) => hasDeveloperRole(owner, repoName, Some(account))
          case None          => false
        }
      }
      case AuthType.DeployKeyType(key) => {
        getDeployKeys(owner, repoName).filter(sshKey => SshUtil.str2PublicKey(sshKey.publicKey).contains(key)) match {
          case List(x) if x.allowWrite => true
          case _                       => false
        }
      }
    }
  }

}

class DefaultGitUploadPack(owner: String, repoName: String)
    extends DefaultGitCommand(owner, repoName)
    with RepositoryService
    with AccountService
    with DeployKeyService {

  override protected def runTask(authType: AuthType): Unit = {
    val execute = Database() withSession { implicit session =>
      getRepository(owner, repoName.replaceFirst("\\.wiki\\Z", ""))
        .map { repositoryInfo =>
          !repositoryInfo.repository.isPrivate || isReadableUser(authType, repositoryInfo)
        }
        .getOrElse(false)
    }

    if (execute) {
      Using.resource(Git.open(getRepositoryDir(owner, repoName))) { git =>
        val repository = git.getRepository
        val upload = new UploadPack(repository)
        upload.upload(in, out, err)
      }
    }
  }
}

class DefaultGitReceivePack(owner: String, repoName: String, baseUrl: String, sshUrl: Option[String])
    extends DefaultGitCommand(owner, repoName)
    with RepositoryService
    with AccountService
    with DeployKeyService {

  override protected def runTask(authType: AuthType): Unit = {
    val execute = Database() withSession { implicit session =>
      getRepository(owner, repoName.replaceFirst("\\.wiki\\Z", ""))
        .map { repositoryInfo =>
          isWritableUser(authType, repositoryInfo)
        }
        .getOrElse(false)
    }

    if (execute) {
      Using.resource(Git.open(getRepositoryDir(owner, repoName))) { git =>
        val repository = git.getRepository
        val receive = new ReceivePack(repository)
        if (!repoName.endsWith(".wiki")) {
          val hook = new CommitLogHook(owner, repoName, userName(authType), baseUrl, sshUrl)
          receive.setPreReceiveHook(hook)
          receive.setPostReceiveHook(hook)
        }
        receive.receive(in, out, err)
      }
    }
  }
}

class PluginGitUploadPack(repoName: String, routing: GitRepositoryRouting)
    extends GitCommand
    with SystemSettingsService {

  override protected def runTask(authType: AuthType): Unit = {
    val execute = Database() withSession { implicit session =>
      routing.filter.filter("/" + repoName, AuthType.userName(authType), loadSystemSettings(), false)
    }

    if (execute) {
      val path = routing.urlPattern.r.replaceFirstIn(repoName, routing.localPath)
      Using.resource(Git.open(new File(Directory.GitBucketHome, path))) { git =>
        val repository = git.getRepository
        val upload = new UploadPack(repository)
        upload.upload(in, out, err)
      }
    }
  }
}

class PluginGitReceivePack(repoName: String, routing: GitRepositoryRouting)
    extends GitCommand
    with SystemSettingsService {

  override protected def runTask(authType: AuthType): Unit = {
    val execute = Database() withSession { implicit session =>
      routing.filter.filter("/" + repoName, AuthType.userName(authType), loadSystemSettings(), true)
    }

    if (execute) {
      val path = routing.urlPattern.r.replaceFirstIn(repoName, routing.localPath)
      Using.resource(Git.open(new File(Directory.GitBucketHome, path))) { git =>
        val repository = git.getRepository
        val receive = new ReceivePack(repository)
        receive.receive(in, out, err)
      }
    }
  }
}

class GitCommandFactory(baseUrl: String, sshUrl: Option[String]) extends CommandFactory {
  private val logger = LoggerFactory.getLogger(classOf[GitCommandFactory])

  override def createCommand(command: String): Command = {
    import GitCommand._
    logger.debug(s"command: $command")

    val pluginCommand = PluginRegistry().getSshCommandProviders.collectFirst {
      case f if f.isDefinedAt(command) => f(command)
    }

    pluginCommand match {
      case Some(x) => x
      case None =>
        command match {
          case SimpleCommandRegex("upload", repoName) if (pluginRepository(repoName)) =>
            new PluginGitUploadPack(repoName, routing(repoName))
          case SimpleCommandRegex("receive", repoName) if (pluginRepository(repoName)) =>
            new PluginGitReceivePack(repoName, routing(repoName))
          case DefaultCommandRegex("upload", owner, repoName) => new DefaultGitUploadPack(owner, repoName)
          case DefaultCommandRegex("receive", owner, repoName) =>
            new DefaultGitReceivePack(owner, repoName, baseUrl, sshUrl)
          case _ => new UnknownCommand(command)
        }
    }
  }

  private def pluginRepository(repoName: String): Boolean =
    PluginRegistry().getRepositoryRouting("/" + repoName).isDefined
  private def routing(repoName: String): GitRepositoryRouting =
    PluginRegistry().getRepositoryRouting("/" + repoName).get

}
