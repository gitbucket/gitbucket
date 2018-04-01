import java.security.MessageDigest
import scala.annotation._
import sbt._
import io._

object Checksums {
  private val bufferSize = 2048

  def generate(source: File, target: File, algorithm: String): Unit =
    sbt.IO write (target, compute(source, algorithm))

  def compute(file: File, algorithm: String): String =
    hex(raw(file, algorithm))

  def raw(file: File, algorithm: String): Array[Byte] =
    (Using fileInputStream file) { is =>
      val md = MessageDigest getInstance algorithm
      val buf = new Array[Byte](bufferSize)
      md.reset()
      @tailrec
      def loop(): Unit = {
        val len = is read buf
        if (len != -1) {
          md update (buf, 0, len)
          loop()
        }
      }
      loop()
      md.digest()
    }

  def hex(bytes: Array[Byte]): String =
    bytes map { it =>
      "%02x" format (it.toInt & 0xff)
    } mkString ""
}
