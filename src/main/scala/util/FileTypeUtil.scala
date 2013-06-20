package util

import org.apache.commons.io.FilenameUtils
import java.net.URLConnection

object FileTypeUtil {
  
  def getMimeType(name: String): String = {
    val fileNameMap = URLConnection.getFileNameMap()
    val mimeType = fileNameMap.getContentTypeFor(name)
    if(mimeType == null){
      "application/octeat-stream"
    } else {
      mimeType
    }
  }
  
  def isImage(name: String): Boolean = getMimeType(name).startsWith("image/")
  
  def isLarge(size: Long): Boolean = (size > 1024 * 1000)
  
  def isText(content: Array[Byte]): Boolean = !content.contains(0)

}