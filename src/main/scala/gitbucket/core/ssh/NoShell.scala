package gitbucket.core.ssh

import gitbucket.core.service.SystemSettingsService
import org.apache.sshd.common.Factory
import org.apache.sshd.server.{Environment, ExitCallback, Command}
import java.io.{OutputStream, InputStream}
import org.eclipse.jgit.lib.Constants

class NoShell extends Factory[Command] with SystemSettingsService {
  override def create(): Command = new Command() {
    private var in: InputStream = null
    private var out: OutputStream = null
    private var err: OutputStream = null
    private var callback: ExitCallback = null

    override def start(env: Environment): Unit = {
      val user = env.getEnv.get("USER")
      val port = loadSystemSettings().sshPort.getOrElse(SystemSettingsService.DefaultSshPort)
      val message =
        """
          | Welcome to
          |   _____   _   _     ____                   _             _
          |  / ____| (_) | |   |  _ \                 | |           | |
          | | |  __   _  | |_  | |_) |  _   _    ___  | | __   ___  | |_
          | | | |_ | | | | __| |  _ <  | | | |  / __| | |/ /  / _ \ | __|
          | | |__| | | | | |_  | |_) | | |_| | | (__  |   <  |  __/ | |_
          |  \_____| |_|  \__| |____/   \__,_|  \___| |_|\_\  \___|  \__|
          |
          | Successfully SSH Access.
          | But interactive shell is disabled.
          |
          | Please use:
          |
          | git clone ssh://%s@GITBUCKET_HOST:%d/OWNER/REPOSITORY_NAME.git
        """.stripMargin.format(user, port).replace("\n", "\r\n") + "\r\n"
      err.write(Constants.encode(message))
      err.flush()
      in.close()
      out.close()
      err.close()
      callback.onExit(127)
    }

    override def destroy(): Unit = {}

    override def setInputStream(in: InputStream): Unit = {
      this.in = in
    }

    override def setOutputStream(out: OutputStream): Unit = {
      this.out = out
    }

    override def setErrorStream(err: OutputStream): Unit = {
      this.err = err
    }

    override def setExitCallback(callback: ExitCallback): Unit = {
      this.callback = callback
    }
  }
}
