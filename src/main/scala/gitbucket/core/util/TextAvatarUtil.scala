package gitbucket.core.util

import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.{Color, Font, Graphics2D, RenderingHints}
import java.awt.font.{FontRenderContext, TextLayout}
import java.awt.geom.AffineTransform


object TextAvatarUtil {
  private val iconSize = 200
  private val fontSize = 180
  private val roundSize = 60
  private val shadowSize = 20

  private val bgSaturation = 0.68f
  private val bgBlightness = 0.73f
  private val shadowBlightness = 0.23f
  private val font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
  private val transparent = new Color(0, 0, 0, 0)

  private def getCenterToDraw(drawText: String, font: Font, w: Int, h: Int): (Int, Int) = {
    val context = new FontRenderContext(new AffineTransform(), true, true)
    val txt = new TextLayout(drawText, font, context)

    val bounds = txt.getBounds

    val x: Int = ((w - bounds.getWidth) / 2 - bounds.getX).toInt
    val y: Int = ((h - bounds.getHeight) / 2 - bounds.getY).toInt
    (x, y)
  }

  private def textImage(drawText: String, bgColor: Color, fgColor: Color): Array[Byte] = {
    val canvas = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB)
    val g = canvas.createGraphics()
    val center = getCenterToDraw(drawText, font, iconSize, iconSize)

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.setColor(transparent)
    g.fillRect(0, 0, iconSize, iconSize)

    g.setColor(bgColor)
    g.fillRoundRect(0, 0, iconSize, iconSize, roundSize, roundSize)

    g.setColor(fgColor)
    g.setFont(font)
    g.drawString(drawText, center._1, center._2)

    g.dispose()

    val stream = new ByteArrayOutputStream
    ImageIO.write(canvas, "png", stream)
    stream.toByteArray
  }

  def textAvatar(nameText: String): Option[Array[Byte]] = {
    val drawText = nameText.substring(0, 1)

    val (bgColor, fgColor) = ColorUtil.getStrandomColors(nameText, bgSaturation, bgBlightness)

    val font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize)
    if (font.canDisplayUpTo(drawText) == -1) Some(textImage(drawText, bgColor, fgColor)) else None
  }

  private def textGroupImage(drawText: String, bgColor: Color, fgColor: Color, shadowColor: Color): Array[Byte] = {
    val canvas = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB)
    val g = canvas.createGraphics()
    val center = getCenterToDraw(drawText, font, iconSize - shadowSize, iconSize - shadowSize)

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.setColor(transparent)
    g.fillRect(0, 0, iconSize, iconSize)

    g.setColor(shadowColor)
    g.fillRect(shadowSize, shadowSize, iconSize, iconSize)

    g.setColor(bgColor)
    g.fillRect(0, 0, iconSize - shadowSize, iconSize - shadowSize)

    g.setColor(fgColor)

    g.setFont(font)
    g.drawString(drawText, center._1, center._2)

    g.dispose()

    val stream = new ByteArrayOutputStream
    ImageIO.write(canvas, "png", stream)
    stream.toByteArray
  }

  def textGroupAvatar(nameText: String): Option[Array[Byte]] = {
    val drawText = nameText.substring(0, 1)

    val (bgColor, fgColor) = ColorUtil.getStrandomColors(nameText, bgSaturation, bgBlightness)
    val shadowColor = bgColor.darker()

    if (font.canDisplayUpTo(drawText) == -1) Some(textGroupImage(drawText, bgColor, fgColor, shadowColor)) else None
  }
}
