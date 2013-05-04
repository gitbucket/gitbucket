package util

import java.io.File
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

object CompressUtil {

  def zip(dest: File, dir: File): Unit = {
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

}