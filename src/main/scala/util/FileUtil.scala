package util

import org.apache.commons.io.{IOUtils, FileUtils}
import java.net.URLConnection
import java.io.File
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}

object FileUtil {
  
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

  def createZipFile(dest: File, dir: File): Unit = {
    def addDirectoryToZip(out: ZipArchiveOutputStream, dir: File, path: String): Unit = {
      dir.listFiles.map { file =>
        if(file.isFile){
          out.putArchiveEntry(new ZipArchiveEntry(path + "/" + file.getName))
          out.write(FileUtils.readFileToByteArray(file))
          out.closeArchiveEntry
        } else if(file.isDirectory){
          addDirectoryToZip(out, file, path + "/" + file.getName)
        }
      }
    }

    val out = new ZipArchiveOutputStream(dest)
    try {
      addDirectoryToZip(out, dir, dir.getName)
    } finally {
      IOUtils.closeQuietly(out)
    }
  }

  def getExtension(name: String): String = {
    val index = name.lastIndexOf('.')
    if(index >= 0){
      name.substring(index + 1)
    } else {
      ""
    }
  }

}