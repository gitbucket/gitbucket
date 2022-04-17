package gitbucket.core.ssh

import gitbucket.core.service.SystemSettingsService.SshAddress
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.{Environment, ExitCallback}
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.shell.ShellFactory

import java.io.{InputStream, OutputStream}
import org.eclipse.jgit.lib.Constants

class NoShell(sshAddress: SshAddress) extends ShellFactory {
  override def createShell(channel: ChannelSession): Command = new Command() {
    private var in: InputStream = null
    private var out: OutputStream = null
    private var err: OutputStream = null
    private var callback: ExitCallback = null

    override def start(channel: ChannelSession, env: Environment): Unit = {
      val placeholderAddress = sshAddress.getUrl("OWNER", "REPOSITORY_NAME")
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
          | git clone %s
        """.stripMargin.format(placeholderAddress).replace("\n", "\r\n") + "\r\n"
      err.write(Constants.encode(message))
      err.flush()
      in.close()
      out.close()
      err.close()
      callback.onExit(127)
    }

    override def destroy(channel: ChannelSession): Unit = {}

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
