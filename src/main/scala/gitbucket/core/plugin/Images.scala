package gitbucket.core.plugin

/**
 * Provides a helper method to generate data URI of images registered by plug-in.
 */
object Images {

  def dataURI(id: String) = s"data:image/png;base64,${PluginRegistry().getImage(id)}"

}
