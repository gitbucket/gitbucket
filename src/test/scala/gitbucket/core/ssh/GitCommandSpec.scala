package gitbucket.core.ssh

import gitbucket.core.service.SystemSettingsService.SshAddress
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.shell.UnknownCommand
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GitCommandFactorySpec extends AnyWordSpec with Matchers {

  trait MockContext {
    val baseUrl = "https://some.example.tech:8080/code-context"
    val sshHost = "localhost"
    val sshPort = 2222
    lazy val factory = new GitCommandFactory(baseUrl, SshAddress(sshHost, sshPort, "git"))
  }

  "createCommand" when {
    val channel = new ChannelSession()
    "receiving a git-receive-pack command" should {
      "return DefaultGitReceivePack" when {
        "the path matches owner/repo" in new MockContext {
          assert(
            factory.createCommand(channel, "git-receive-pack '/owner/repo.git'").isInstanceOf[DefaultGitReceivePack]
          )
          assert(
            factory
              .createCommand(channel, "git-receive-pack '/owner/repo.wiki.git'")
              .isInstanceOf[DefaultGitReceivePack]
          )
        }
        "the leading slash is left off and running on port 22" in new MockContext {
          override val sshPort: Int = 22
          assert(
            factory.createCommand(channel, "git-receive-pack 'owner/repo.git'").isInstanceOf[DefaultGitReceivePack]
          )
          assert(
            factory.createCommand(channel, "git-receive-pack 'owner/repo.wiki.git'").isInstanceOf[DefaultGitReceivePack]
          )
        }
      }
      "return UnknownCommand" when {
        "the ssh port is not 22 and the leading slash is missing" in new MockContext {
          override val sshPort: Int = 1337
          assert(factory.createCommand(channel, "git-receive-pack 'owner/repo.git'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-receive-pack 'owner/repo.wiki.git'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-receive-pack 'oranges.git'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-receive-pack 'apples.git'").isInstanceOf[UnknownCommand])
        }
        "the path is malformed" in new MockContext {
          assert(
            factory.createCommand(channel, "git-receive-pack '/owner/repo/wrong.git'").isInstanceOf[UnknownCommand]
          )
          assert(factory.createCommand(channel, "git-receive-pack '/owner:repo.wiki.git'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-receive-pack '/oranges'").isInstanceOf[UnknownCommand])
        }
      }
    }
    "receiving a git-upload-pack command" should {
      "return DefaultGitUploadPack" when {
        "the path matches owner/repo" in new MockContext {
          assert(factory.createCommand(channel, "git-upload-pack '/owner/repo.git'").isInstanceOf[DefaultGitUploadPack])
          assert(
            factory.createCommand(channel, "git-upload-pack '/owner/repo.wiki.git'").isInstanceOf[DefaultGitUploadPack]
          )
        }
        "the leading slash is left off and running on port 22" in new MockContext {
          override val sshPort = 22
          assert(factory.createCommand(channel, "git-upload-pack 'owner/repo.git'").isInstanceOf[DefaultGitUploadPack])
          assert(
            factory.createCommand(channel, "git-upload-pack 'owner/repo.wiki.git'").isInstanceOf[DefaultGitUploadPack]
          )
        }
      }
      "return UnknownCommand" when {
        "the ssh port is not 22 and the leading slash is missing" in new MockContext {
          override val sshPort = 1337
          assert(factory.createCommand(channel, "git-upload-pack 'owner/repo.git'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-upload-pack 'owner/repo.wiki.git'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-upload-pack 'oranges.git'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-upload-pack 'apples.git'").isInstanceOf[UnknownCommand])
        }
        "the path is malformed" in new MockContext {
          assert(factory.createCommand(channel, "git-upload-pack '/owner/repo'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-upload-pack '/owner:repo.wiki.git'").isInstanceOf[UnknownCommand])
          assert(factory.createCommand(channel, "git-upload-pack '/oranges'").isInstanceOf[UnknownCommand])
        }
      }
    }
    "receiving any command not matching git-(receive|upload)-pack" should {
      "return UnknownCommand" in new MockContext {
        assert(factory.createCommand(channel, "git-destroy-pack '/owner/repo.git'").isInstanceOf[UnknownCommand])
        assert(factory.createCommand(channel, "git-irrigate-pack '/apples.git'").isInstanceOf[UnknownCommand])
        assert(factory.createCommand(channel, "git-force-push '/stolen/nuke.git'").isInstanceOf[UnknownCommand])
        assert(factory.createCommand(channel, "git-delete '/backups.git'").isInstanceOf[UnknownCommand])
        assert(factory.createCommand(channel, "git-pack '/your/bags.git'").isInstanceOf[UnknownCommand])
        assert(factory.createCommand(channel, "git- '/bananas.git'").isInstanceOf[UnknownCommand])
        assert(factory.createCommand(channel, "99 tickets of bugs on the wall").isInstanceOf[UnknownCommand])
      }
    }
  }
}
