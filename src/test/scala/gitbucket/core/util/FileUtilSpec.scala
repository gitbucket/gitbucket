package gitbucket.core.util

import org.scalatest.funsuite.AnyFunSuite

class FileUtilSpec extends AnyFunSuite {

  test("getSafeMimeType") {
    val contentType1 = FileUtil.getSafeMimeType("test.svg", true)
    assert(contentType1 == "text/plain; charset=UTF-8")

    val contentType2 = FileUtil.getSafeMimeType("test.svg", false)
    assert(contentType2 == "image/svg+xml")

    // Markup that a browser renders as a page (and runs its scripts) is shown as text
    Seq("test.html", "test.htm", "test.xhtml", "test.xht", "test.xml", "test.xsl", "test.xsd", "test.rss").foreach {
      name =>
        assert(FileUtil.getSafeMimeType(name, false) == "text/plain", name)
    }
    assert(FileUtil.getSafeMimeType("test.png") == "image/png")
    assert(FileUtil.getSafeMimeType("test.pdf") == "application/pdf")
  }

}
