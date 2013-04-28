package util

import org.apache.commons.io.FilenameUtils

object FileTypeUtil {
  
  def isImage(name: String): Boolean = FilenameUtils.getExtension(name).toLowerCase match {
    case "jpg"|"jpeg"|"bmp"|"gif"|"png" => true
    case _ => false
  }
  
  def isLarge(size: Long): Boolean = (size > 1024 * 1000)

}