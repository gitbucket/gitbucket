package gitbucket.core.ssh

import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.shell.UnknownCommand
import org.scalatest.funspec.AnyFunSpec

class GitCommandFactorySpec extends AnyFunSpec {

  val factory = new GitCommandFactory("http://localhost:8080", None)

  describe("createCommand") {
    val channel = new ChannelSession()
    it("should return GitReceivePack when command is git-receive-pack") {
      assert(
        factory.createCommand(channel, "git-receive-pack '/owner/repo.git'").isInstanceOf[DefaultGitReceivePack] == true
      )
      assert(
        factory
          .createCommand(channel, "git-receive-pack '/owner/repo.wiki.git'")
          .isInstanceOf[DefaultGitReceivePack] == true
      )
    }
    it("should return GitUploadPack when command is git-upload-pack") {
      assert(
        factory.createCommand(channel, "git-upload-pack '/owner/repo.git'").isInstanceOf[DefaultGitUploadPack] == true
      )
      assert(
        factory
          .createCommand(channel, "git-upload-pack '/owner/repo.wiki.git'")
          .isInstanceOf[DefaultGitUploadPack] == true
      )
    }
    it("should return UnknownCommand when command is not git-(upload|receive)-pack") {
      assert(factory.createCommand(channel, "git- '/owner/repo.git'").isInstanceOf[UnknownCommand] == true)
      assert(factory.createCommand(channel, "git-pack '/owner/repo.git'").isInstanceOf[UnknownCommand] == true)
      assert(factory.createCommand(channel, "git-a-pack '/owner/repo.git'").isInstanceOf[UnknownCommand] == true)
      assert(factory.createCommand(channel, "git-up-pack '/owner/repo.git'").isInstanceOf[UnknownCommand] == true)
      assert(factory.createCommand(channel, "\ngit-upload-pack '/owner/repo.git'").isInstanceOf[UnknownCommand] == true)
    }
    it("should return UnknownCommand when git command has no valid arguments") {
      // must be: git-upload-pack '/owner/repository_name.git'
      assert(factory.createCommand(channel, "git-upload-pack").isInstanceOf[UnknownCommand] == true)
      assert(factory.createCommand(channel, "git-upload-pack /owner/repo.git").isInstanceOf[UnknownCommand] == true)
      assert(factory.createCommand(channel, "git-upload-pack 'owner/repo.git'").isInstanceOf[UnknownCommand] == true)
      assert(factory.createCommand(channel, "git-upload-pack '/ownerrepo.git'").isInstanceOf[UnknownCommand] == true)
      assert(factory.createCommand(channel, "git-upload-pack '/owner/repo.wiki'").isInstanceOf[UnknownCommand] == true)
    }
  }

}
