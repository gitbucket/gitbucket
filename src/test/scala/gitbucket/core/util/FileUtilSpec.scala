package gitbucket.core.util

import org.scalatest.funsuite.AnyFunSuite

class FileUtilSpec extends AnyFunSuite {

  test("getSafeMimeType") {
    val contentType1 = FileUtil.getSafeMimeType("test.svg", true)
    assert(contentType1 == "text/plain; charset=UTF-8")

    val contentType2 = FileUtil.getSafeMimeType("test.svg", false)
    assert(contentType2 == "image/svg+xml")
  }

}
