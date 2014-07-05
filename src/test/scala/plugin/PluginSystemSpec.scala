package plugin

import org.specs2.mutable._

class PluginSystemSpec extends Specification {

  "isUpdatable" should {
    "return true for updattable plugin" in {
      PluginSystem.isUpdatable("1.0.0", "1.0.1") must beTrue
      PluginSystem.isUpdatable("1.0.0", "1.1.0") must beTrue
      PluginSystem.isUpdatable("1.1.1", "1.2.0") must beTrue
      PluginSystem.isUpdatable("1.2.1", "2.0.0") must beTrue
    }
    "return false for not updattable plugin" in {
      PluginSystem.isUpdatable("1.0.0", "1.0.0") must beFalse
      PluginSystem.isUpdatable("1.0.1", "1.0.0") must beFalse
      PluginSystem.isUpdatable("1.1.1", "1.1.0") must beFalse
      PluginSystem.isUpdatable("2.0.0", "1.2.1") must beFalse
    }
  }

}
