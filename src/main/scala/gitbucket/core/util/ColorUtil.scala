package gitbucket.core.util

import java.awt.Color

object ColorUtil {
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

  def goodContrastColor(base: Color, c1: Color, c2: Color): Color = {
    if (contrastRatio(base, c1) > contrastRatio(base, c2)) c1 else c2
  }

  private def strToHue(text: String): Float = {
    Integer.parseInt(StringUtil.md5(text).substring(0, 2), 16) / 256f
  }

  /**
    * random color generation from String.
    * @param text seed String. same string generates same color.
    * @param saturation saturation to generate color.
    * @param brightness blightness to generate color.
    * @return (random generated color, Black or White Color)
    */
  def getStrandomColors(text: String, saturation: Float, blightness: Float): (Color, Color) = {
    val bgHue = strToHue(text)
    val bgColor = Color.getHSBColor(bgHue, saturation, blightness)
    val fgColor = goodContrastColor(bgColor, Color.WHITE, Color.BLACK)
    (bgColor, fgColor)
  }

}

