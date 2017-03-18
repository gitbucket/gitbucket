package gitbucket.core.util

import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.{Color, Font, RenderingHints}
import java.awt.font.{FontRenderContext, TextLayout}

object TextAvatarUtil {
  // https://www.w3.org/TR/2008/REC-WCAG20-20081211/#relativeluminancedef
  private def relativeLuminance(c: Color): Double = {
    val rgb = Seq(c.getRed, c.getGreen, c.getBlue).map{_/255.0}.map{x => if (x <= 0.03928) x / 12.92 else math.pow((x + 0.055) / 1.055, 2.4)}
    0.2126 * rgb(0) + 0.7152 * rgb(1) + 0.0722 * rgb(2)
  }

  // https://www.w3.org/TR/2008/REC-WCAG20-20081211/#contrast-ratiodef
  private def contrastRatio(c1: Color, c2: Color): Double = {
    val l1 = relativeLuminance(c1)
    val l2 = relativeLuminance(c2)
    if (l1 > l2) (l1 + 0.05) / (l2 + 0.05) else (l2 + 0.05) / (l1 + 0.05)
  }

  private def goodContrastColor(base: Color, c1: Color, c2: Color): Color = {
    if (contrastRatio(base, c1) > contrastRatio(base, c2)) c1 else c2
  }

  private def textImage(w: Int, h: Int, drawText: String, font: Font, fontSize: Int, bgColor: Color, fgColor: Color): Array[Byte] = {
    val canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = canvas.createGraphics()

    g.setColor(new Color(0, 0, 0, 0))
    g.fillRect(0, 0, w, h)
    g.setColor(bgColor)
    g.fillRoundRect(0, 0, w, h, 60, 60)

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.setColor(fgColor)
    val context = g.getFontRenderContext
    val txt = new TextLayout(drawText, font, context)
    val bounds = txt.getBounds

    val x: Int = ((w - bounds.getWidth) / 2 - bounds.getX).toInt
    val y: Int = ((h - bounds.getHeight) / 2 - bounds.getY).toInt

    g.setFont(font)
    g.drawString(drawText, x, y)

    g.dispose()

    val stream = new ByteArrayOutputStream
    ImageIO.write(canvas, "png", stream)
    stream.toByteArray
  }

  def textAvatar(nameText: String): Option[Array[Byte]] = {
    val drawText = nameText.substring(0, 1)
    val md5 = StringUtil.md5(nameText)
    val hashedInt = Integer.parseInt(md5.substring(0, 2), 16)

    val bgHue = hashedInt / 256f
    val bgSaturation = 0.68f
    val bgBlightness = 0.73f
    val bgColor = Color.getHSBColor(bgHue, bgSaturation, bgBlightness)
    val fgColor = goodContrastColor(bgColor, Color.BLACK, Color.WHITE)

    val size = (200, 200)
    val fontSize = 180
    val font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
    if (font.canDisplayUpTo(drawText) == -1) Some(textImage(size._1, size._2, drawText, font, fontSize, bgColor, fgColor)) else None
  }
}
