package gitbucket.core.service

import gitbucket.core.service.SystemSettingsService.SshAddress
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Properties

class SystemSettingsServiceSpec extends AnyWordSpecLike with Matchers {

  "loadSystemSettings" should {
    "read old-style ssh configuration" in new SystemSettingsService {
      val props = new Properties()
      props.setProperty("ssh", "true")
      props.setProperty("ssh.host", "127.0.0.1")
      props.setProperty("ssh.port", "8022")

      val settings = loadSystemSettings(props)
      settings.ssh.enabled shouldBe true
      settings.ssh.bindAddress shouldBe Some(SshAddress("127.0.0.1", 8022, "git"))
      settings.ssh.publicAddress shouldBe settings.ssh.bindAddress
    }
    "read new-style ssh configuration" in new SystemSettingsService {
      val props = new Properties()
      props.setProperty("ssh", "true")
      props.setProperty("ssh.bindAddress.host", "127.0.0.1")
      props.setProperty("ssh.bindAddress.port", "8022")
      props.setProperty("ssh.publicAddress.host", "code.these.solutions")
      props.setProperty("ssh.publicAddress.port", "22")

      val settings = loadSystemSettings(props)
      settings.ssh.enabled shouldBe true
      settings.ssh.bindAddress shouldBe Some(SshAddress("127.0.0.1", 8022, "git"))
      settings.ssh.publicAddress shouldBe Some(SshAddress("code.these.solutions", 22, "git"))
    }
    "default the ssh port if not specified" in new SystemSettingsService {
      val props = new Properties()
      props.setProperty("ssh", "true")
      props.setProperty("ssh.bindAddress.host", "127.0.0.1")
      props.setProperty("ssh.publicAddress.host", "code.these.solutions")

      val settings = loadSystemSettings(props)
      settings.ssh.enabled shouldBe true
      settings.ssh.bindAddress shouldBe Some(SshAddress("127.0.0.1", 29418, "git"))
      settings.ssh.publicAddress shouldBe Some(SshAddress("code.these.solutions", 22, "git"))
    }
    "default the public address if not specified" in new SystemSettingsService {
      val props = new Properties()
      props.setProperty("ssh", "true")
      props.setProperty("ssh.bindAddress.host", "127.0.0.1")
      props.setProperty("ssh.bindAddress.port", "8022")

      val settings = loadSystemSettings(props)
      settings.ssh.enabled shouldBe true
      settings.ssh.bindAddress shouldBe Some(SshAddress("127.0.0.1", 8022, "git"))
      settings.ssh.publicAddress shouldBe settings.ssh.bindAddress
    }
    "return addresses even if ssh is not enabled" in new SystemSettingsService {
      val props = new Properties()
      props.setProperty("ssh", "false")
      props.setProperty("ssh.bindAddress.host", "127.0.0.1")
      props.setProperty("ssh.bindAddress.port", "8022")
      props.setProperty("ssh.publicAddress.host", "code.these.solutions")
      props.setProperty("ssh.publicAddress.port", "22")

      val settings = loadSystemSettings(props)
      settings.ssh.enabled shouldBe false
      settings.ssh.bindAddress shouldNot be(empty)
      settings.ssh.publicAddress shouldNot be(empty)
    }
  }

  "SshAddress" can {
    trait MockContext {
      val host = "code.these.solutions"
      val port = 1337
      val user = "git"
      lazy val sshAddress = SshAddress(host, port, user)
    }
    "isDefaultPort" which {
      "returns true if using port 22" in new MockContext {
        override val port = 22
        sshAddress.isDefaultPort shouldBe true
      }
      "returns false if using a different port" in new MockContext {
        override val port = 8022
        sshAddress.isDefaultPort shouldBe false
      }
    }
    "getUrl" which {
      "returns the port number when not using port 22" in new MockContext {
        override val port = 8022
        sshAddress.getUrl shouldBe "ssh://git@code.these.solutions:8022"
      }
      "leaves off the port number when using port 22" in new MockContext {
        override val port = 22
        sshAddress.getUrl shouldBe "git@code.these.solutions"
      }
    }
    "getUrl for owner and repo" which {
      "returns an ssh-protocol url when not using port 22" in new MockContext {
        override val port = 8022
        sshAddress.getUrl("np-hard", "quantum-crypto-cracker") shouldBe
          "ssh://git@code.these.solutions:8022/np-hard/quantum-crypto-cracker.git"
      }
      "returns a bare-protocol url when using port 22" in new MockContext {
        override val port = 22
        sshAddress.getUrl("syntactic", "brace-stretcher") shouldBe
          "git@code.these.solutions:syntactic/brace-stretcher.git"
      }
    }
  }
}
