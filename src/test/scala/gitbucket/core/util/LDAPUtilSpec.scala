package gitbucket.core.util

import org.scalatest.funsuite.AnyFunSuite

class LDAPUtilSpec extends AnyFunSuite {

  test("escapeFilterValue") {
    assert(LDAPUtil.escapeFilterValue("alice@example.com") == "alice@example.com")
    assert(LDAPUtil.escapeFilterValue("root*") == "root\\2a")
    assert(LDAPUtil.escapeFilterValue("a(b)c\\d\u0000") == "a\\28b\\29c\\5cd\\00")
  }
}
