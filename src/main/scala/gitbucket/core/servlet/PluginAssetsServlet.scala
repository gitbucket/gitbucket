package gitbucket.core.servlet

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.util.FileUtil
import org.apache.commons.io.IOUtils

/**
 * Supply assets which are provided by plugins.
 */
class PluginAssetsServlet extends HttpServlet {

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    val assetsMappings = PluginRegistry().getAssetsMappings
    val path = req.getRequestURI.substring(req.getContextPath.length)

    assetsMappings
      .find    { case (prefix, _, _) => path.startsWith("/plugin-assets" + prefix) }
      .flatMap { case (prefix, resourcePath, classLoader) =>
        val resourceName = path.substring(("/plugin-assets" + prefix).length)
        Option(classLoader.getResourceAsStream(resourcePath.replaceFirst("^/", "") + resourceName))
      }
      .map { in =>
        try {
          val bytes = IOUtils.toByteArray(in)
          resp.setContentLength(bytes.length)
          resp.setContentType(FileUtil.getContentType(path, bytes))
          resp.getOutputStream.write(bytes)
        } finally {
          in.close()
        }
      }
      .getOrElse {
        resp.setStatus(404)
      }
  }

}
