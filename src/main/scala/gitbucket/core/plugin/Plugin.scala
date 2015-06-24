package gitbucket.core.plugin

import javax.servlet.ServletContext
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Version

/**
 * Trait for define plugin interface.
 * To provide plugin, put Plugin class which mixed in this trait into the package root.
 */
trait Plugin {

  val pluginId: String
  val pluginName: String
  val description: String
  val versions: Seq[Version]

  /**
   * Override to declare this plug-in provides images.
   */
  val images: Seq[(String, Array[Byte])] = Nil

  /**
   * Override to declare this plug-in provides controllers.
   */
  val controllers: Seq[(String, ControllerBase)] = Nil

  /**
   * Override to declare this plug-in provides JavaScript.
   */
  val javaScripts: Seq[(String, String)] = Nil

  /**
   * Override to declare this plug-in provides renderers.
   */
  val renderers: Seq[(String, Renderer)] = Nil

  /**
   * This method is invoked in initialization of plugin system.
   * Register plugin functionality to PluginRegistry.
   */
  def initialize(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Unit = {
    images.foreach { case (id, in) =>
      registry.addImage(id, in)
    }
    controllers.foreach { case (path, controller) =>
      registry.addController(path, controller)
    }
    javaScripts.foreach { case (path, script) =>
      registry.addJavaScript(path, script)
    }
    renderers.foreach { case (extension, renderer) =>
      registry.addRenderer(extension, renderer)
    }
  }

  /**
   * This method is invoked in shutdown of plugin system.
   * If the plugin has any resources, release them in this method.
   */
  def shutdown(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Unit = {}

  /**
   * Helper method to get a resource from classpath.
   */
  protected def fromClassPath(path: String): Array[Byte] =
    using(getClass.getClassLoader.getResourceAsStream(path)){ in =>
      val bytes = new Array[Byte](in.available)
      in.read(bytes)
      bytes
    }

}
