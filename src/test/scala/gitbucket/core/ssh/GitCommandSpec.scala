package gitbucket.core.ssh

import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.apache.sshd.server.command.UnknownCommand
import javax.servlet.ServletContext

class GitCommandFactorySpec extends Specification with Mockito {

  val factory = new GitCommandFactory("http://localhost:8080")

  "createCommand" should {
    "returns GitReceivePack when command is git-receive-pack" in {
      factory.createCommand("git-receive-pack '/owner/repo.git'").isInstanceOf[DefaultGitReceivePack] must beTrue
      factory.createCommand("git-receive-pack '/owner/repo.wiki.git'").isInstanceOf[DefaultGitReceivePack] must beTrue

    }
    "returns GitUploadPack when command is git-upload-pack" in {
      factory.createCommand("git-upload-pack '/owner/repo.git'").isInstanceOf[DefaultGitUploadPack] must beTrue
      factory.createCommand("git-upload-pack '/owner/repo.wiki.git'").isInstanceOf[DefaultGitUploadPack] must beTrue

    }
    "returns UnknownCommand when command is not git-(upload|receive)-pack" in {
      factory.createCommand("git- '/owner/repo.git'").isInstanceOf[UnknownCommand] must beTrue
      factory.createCommand("git-pack '/owner/repo.git'").isInstanceOf[UnknownCommand] must beTrue
      factory.createCommand("git-a-pack '/owner/repo.git'").isInstanceOf[UnknownCommand] must beTrue
      factory.createCommand("git-up-pack '/owner/repo.git'").isInstanceOf[UnknownCommand] must beTrue
      factory.createCommand("\ngit-upload-pack '/owner/repo.git'").isInstanceOf[UnknownCommand] must beTrue
    }
    "returns UnknownCommand when git command has no valid arguments" in {
      // must be: git-upload-pack '/owner/repository_name.git'
      factory.createCommand("git-upload-pack").isInstanceOf[UnknownCommand] must beTrue
      factory.createCommand("git-upload-pack /owner/repo.git").isInstanceOf[UnknownCommand] must beTrue
      factory.createCommand("git-upload-pack 'owner/repo.git'").isInstanceOf[UnknownCommand] must beTrue
      factory.createCommand("git-upload-pack '/ownerrepo.git'").isInstanceOf[UnknownCommand] must beTrue
      factory.createCommand("git-upload-pack '/owner/repo.wiki'").isInstanceOf[UnknownCommand] must beTrue
    }
  }

}
