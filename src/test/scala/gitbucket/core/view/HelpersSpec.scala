package gitbucket.core.view

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService.RepositoryInfo
import org.scalatest.FunSpec
import org.scalatestplus.mockito.MockitoSugar
import java.util.Date
import java.util.TimeZone

class HelpersSpec extends FunSpec with MockitoSugar {

  private implicit val context = mock[Context]
  private val repository = mock[RepositoryInfo]

  import helpers._

  describe("urlLink and decorateHtml") {

    it("should pass identical string when no link is present") {
      val before = "Description"
      val after = decorateHtml(urlLink(before), repository)
      assert(after == before)
    }

    it("should convert a single link") {
      val before = "http://example.com"
      val after = decorateHtml(urlLink(before), repository)
      assert(after == """<a href="http://example.com">http://example.com</a>""")
    }

    it("should convert a single link within trailing text") {
      val before = "Example Project. http://example.com"
      val after = decorateHtml(urlLink(before), repository)
      assert(after == """Example Project. <a href="http://example.com">http://example.com</a>""")
    }

    it("should convert a multiple links within text") {
      val before = "Example Project. http://example.com. (See also https://github.com/)"
      val after = decorateHtml(urlLink(before), repository)
      assert(
        after == """Example Project. <a href="http://example.com">http://example.com</a>. (See also <a href="https://github.com/">https://github.com/</a>)"""
      )
    }

    it("should properly escape html metacharacters") {
      val before = "<>&"
      val after = decorateHtml(urlLink(before), repository)
      assert(after == """&lt;&gt;&amp;""")
    }

    it("should escape html metacharacters adjacent to a link") {
      val before = "<http://example.com>"
      val after = decorateHtml(urlLink(before), repository)
      assert(after == """&lt;<a href="http://example.com">http://example.com</a>&gt;""")
    }

    it("should stop link recognition at a metacharacter") {
      val before = "http://exa<mple.com"
      val after = decorateHtml(urlLink(before), repository)
      assert(after == """<a href="http://exa">http://exa</a>&lt;mple.com""")
    }

    it("should make sure there are no double quotes in the href attribute") {
      val before = "http://exa\"mple.com"
      val after = decorateHtml(urlLink(before), repository)
      assert(after == """<a href="http://exa&quot;mple.com">http://exa"mple.com</a>""")
    }
  }

  describe("datetimeAgo") {
    it("should render a time within a minute") {
      val time = System.currentTimeMillis()
      val datetime = datetimeAgo(new Date(time))
      assert(datetime == "just now")
    }

    it("should render a time 1 minute ago") {
      val time = System.currentTimeMillis() - (60 * 1000)
      val datetime = datetimeAgo(new Date(time))
      assert(datetime == "1 minute ago")
    }

    it("should render a time 2 minute ago") {
      val time = System.currentTimeMillis() - (60 * 1000 * 2)
      val datetime = datetimeAgo(new Date(time))
      assert(datetime == "2 minutes ago")
    }
  }

  describe("datetimeRFC3339") {
    it("should format date as RFC3339 format") {
      val time = 1546961077224L
      val datetime = datetimeRFC3339(new Date(time))
      assert(datetime == "2019-01-08T15:24:37Z")
    }
  }

  describe("date") {
    it("should format date as yyyy-MM-dd with default timezone") {
      val defaultTimeZone = TimeZone.getDefault

      try {
        val time = 1546961077247L
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val datetimeUTC = date(new Date(time))
        assert(datetimeUTC == "2019-01-08")

        TimeZone.setDefault(TimeZone.getTimeZone("JST"))
        val datetimeJST = date(new Date(time))
        assert(datetimeJST == "2019-01-09")

      } finally {
        TimeZone.setDefault(defaultTimeZone)
      }
    }
  }

  describe("hashDate") {
    it("should format date as yyyyMMDDHHmmss with default timezone") {
      val defaultTimeZone = TimeZone.getDefault

      try {
        val time = 1546961077247L
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val hash = hashDate(new Date(time))
        assert(hash == "20190108152437")
      } finally {
        TimeZone.setDefault(defaultTimeZone)
      }
    }
  }

  describe("hashQuery") {
    it("should return same value for multiple calls") {
      val time = 1546961077247L
      val hash1 = hashQuery
      Thread.sleep(500)
      val hash2 = hashQuery
      assert(hash1 == hash2)
    }
  }
}
