package gitbucket.core.plugin

import java.util.Base64

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService
import gitbucket.core.util.JGitUtil.ContentInfo
import gitbucket.core.util.StringUtil
import gitbucket.core.view.helpers
import gitbucket.core.view.helpers.urlLink
import org.apache.http.util.TextUtils
import play.twirl.api.Html

import scala.collection.immutable

/**
 * New style Renderer
 */
trait EnhancedRenderer {
  def renderContent(request: EnhancedRenderRequest): Html = {
    this match {
      case r: TextRenderer =>
        r.renderAsText(request)
      case r: ImageRenderer =>
        r.renderAsImage(request, "")
      case r: ObjectRenderer =>
        r.renderAsObject(request)
      case _ =>
        Html("??????")
    }
  }

  val allowLargeFile: Boolean = false
}

trait TextRenderer extends EnhancedRenderer {
  def renderAsText(request: EnhancedRenderRequest): Html
  def renderMarkup(request: RenderRequest): Html
}

trait ImageRenderer extends EnhancedRenderer {
  def renderAsImage(request: EnhancedRenderRequest, attr: String): Html
}

trait TextImageRenderer extends TextRenderer with ImageRenderer {
  override def renderContent(request: EnhancedRenderRequest): Html = {
    new Html(immutable.Seq[Html](renderAsImage(request, ""), renderAsText(request)))
  }
}

trait ObjectRenderer extends EnhancedRenderer {
  def renderAsObject(request: EnhancedRenderRequest): Html
}

trait DiffRenderer extends EnhancedRenderer {
  def renderDiff(oldRequest: EnhancedRenderRequest, newRequest: EnhancedRenderRequest): Html
}

case class EnhancedRenderRequest(
  filePath: List[String],
  contentInfo: ContentInfo,
  branch: String,
  repository: RepositoryService.RepositoryInfo,
  enableWikiLink: Boolean,
  enableRefsLink: Boolean,
  enableAnchor: Boolean,
  context: Context
) {
  val rawPath = s"""${helpers.url(repository)(context)}/raw/${branch}/${filePath.mkString("/")}"""
}

object SVGRenderer extends TextImageRenderer {
  override def renderAsText(request: EnhancedRenderRequest): Html = {
    Html(s"""<pre class="xml">${StringUtil.escapeHtml(request.contentInfo.content)}</pre>""")
  }

  override def renderAsImage(request: EnhancedRenderRequest, attr: String): Html = {
    Html(s"""<img src="${request.rawPath}" ${attr} />""")
  }

  override def renderMarkup(request: RenderRequest): Html = {
    Html(request.fileContent)
  }
}

class ImgRenderer(mimeType: String) extends ImageRenderer {
  override def renderAsImage(request: EnhancedRenderRequest, attr: String): Html = {
    val base64 = Base64.getEncoder.encodeToString(request.contentInfo.contentBytes)
    Html(s"""<img src="data:${mimeType};base64,${base64}" ${attr}>""")
  }
}

object VideoRenderer extends ObjectRenderer {
  override val allowLargeFile: Boolean = true

  override def renderAsObject(request: EnhancedRenderRequest): Html = {
    Html(s"""<video src="${request.rawPath}" controls width="100%" />""")
  }
}

object AudioRenderer extends ObjectRenderer {
  override val allowLargeFile: Boolean = true

  override def renderAsObject(request: EnhancedRenderRequest): Html = {
    Html(s"""<audio src="${request.rawPath}" controls width="100%" />""")
  }
}

object PdfRenderer extends ObjectRenderer {
  override val allowLargeFile: Boolean = true

  override def renderAsObject(request: EnhancedRenderRequest): Html = {
    Html(s"""<object data="${request.rawPath}" width="100%" height="700">
            |<p>Your browser does not support render inline PDF. Please download from this
            | <a href="${request.rawPath}">Link</a></p>
            |</object>""".stripMargin)
  }
}

object LargeFileRenderer extends EnhancedRenderer {
  override def renderContent(request: EnhancedRenderRequest): Html = {
    Html(s"""<div class="box-content-bottom" style="text-align: center; padding-top: 20px; padding-bottom: 20px;">
            |<a href="${request.rawPath}">View Raw</a>
            |<br><br>(Sorry about that, but we can't show files that are this big right now)</div>""".stripMargin)
  }
}

object DefaultBinaryRenderer extends EnhancedRenderer {
  override def renderContent(request: EnhancedRenderRequest): Html = {
    Html(s"""<div class="box-content-bottom" style="text-align: center; padding-top: 20px; padding-bottom: 20px;">
            |<a href="${request.rawPath}">View Raw</a>
            |<br><br>(Sorry about that, but we can't show files that are this big right now)</div>""".stripMargin)
  }
}

object DefaultTextRenderer extends TextRenderer {
  override def renderAsText(request: EnhancedRenderRequest): Html = {
    Html(s"""<tt><pre class="plain">${urlLink(request.contentInfo.content)}</pre></tt>""")
  }

  override def renderMarkup(request: RenderRequest): Html = {
    Html(s"""<tt><pre class="plain">${urlLink(request.fileContent)}</pre></tt>""")
  }
}

class RendererWrapper(renderer: Renderer) extends TextRenderer {
  override def renderMarkup(request: RenderRequest): Html = {
    renderer.render(request)
  }

  override def renderAsText(request: EnhancedRenderRequest): Html = {
    renderer.render(
      RenderRequest(
        request.filePath,
        request.contentInfo.content,
        request.branch,
        request.repository,
        request.enableWikiLink,
        request.enableRefsLink,
        request.enableAnchor,
        request.context
      )
    )
  }
}
