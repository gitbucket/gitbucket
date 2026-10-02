package gitbucket.core.service

import gitbucket.core.util.Directory
import org.apache.commons.io.FileUtils
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

class WikiServiceSpec extends AnyFunSuite with ServiceSpecBase with WikiService {

  test("getWikiPage returns None for a deleted page, so that it can be created again") {
    withTestDB { implicit session =>
      val owner = "wiki-" + Random.alphanumeric.take(10).mkString
      val account = generateNewAccount(owner)
      try {
        createWikiRepository(account, owner, "repo", "main")
        saveWikiPage(owner, "repo", "", "Page", "first", account, "Create Page", None)
        assert(getWikiPage(owner, "repo", "Page", "main").map(_.content).contains("first"))

        deleteWikiPage(owner, "repo", "Page", account.fullName, account.mailAddress, "Destroy Page")
        assert(getWikiPage(owner, "repo", "Page", "main").isEmpty)
        assert(!getWikiPageList(owner, "repo", "main").contains("Page"))
        assert(getWikiPage(owner, "repo", "Home", "main").isDefined, "other pages are not affected")

        saveWikiPage(owner, "repo", "", "Page", "second", account, "Create Page again", None)
        assert(getWikiPage(owner, "repo", "Page", "main").map(_.content).contains("second"))
      } finally {
        FileUtils.deleteQuietly(Directory.getWikiRepositoryDir(owner, "repo"))
      }
    }
  }
}
