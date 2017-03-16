package gitbucket.core.service

import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.{Color, Font, RenderingHints}
import java.awt.font.{FontRenderContext, TextLayout}
import gitbucket.core.util.StringUtil

trait TextAvatarService {
  def textAvatar(nameText: String): Array[Byte] = {
    val drawText = nameText.substring(0, 1)
    val md5 = StringUtil.md5(nameText)
    val hashedInt = Integer.parseInt(md5.substring(0, 2), 16)

    val h = hashedInt / 256f
    val bgColor = Color.getHSBColor(h, 1f, 1f)
    val fgColor = Color.getHSBColor(h + 0.5f, 1f, 0.8f)

    val size = (200, 200)
    val canvas = new BufferedImage(size._1, size._2, BufferedImage.TYPE_INT_ARGB)
    val g = canvas.createGraphics()

    g.setColor(new Color(0, 0, 0, 0))
    g.fillRect(0, 0, canvas.getWidth, canvas.getHeight)
    g.setColor(bgColor)
    g.fillRoundRect(0, 0, canvas.getWidth, canvas.getHeight, 60, 60)

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.setColor(fgColor)
    val font = new Font("SansSerif", Font.PLAIN, 180)
    val context = g.getFontRenderContext
    val txt = new TextLayout(drawText, font, context)
    val bounds = txt.getBounds

    val x: Int = ((size._1 - bounds.getWidth) / 2 - bounds.getX).toInt
    val y: Int = ((size._2 - bounds.getHeight) / 2 - bounds.getY).toInt

    g.setFont(font)
    g.drawString(drawText, x, y)

    g.dispose()

    val stream = new ByteArrayOutputStream
    ImageIO.write(canvas, "png", stream)
    stream.toByteArray
  }
}
