package gitbucket.core.view

import gitbucket.core.util.SyntaxSugars
import SyntaxSugars._
import org.scalatest.funspec.AnyFunSpec

class PaginationSpec extends AnyFunSpec {

  describe("max") {
    it("should return max page number") {
      val pagination = Pagination(1, 100, 10, 6)
      assert(pagination.max == 10)
    }
  }

  describe("omitLeft and omitRight") {
    it("should return true if pagination links at their side will be omitted") {
      defining(Pagination(1, 100, 10, 6)) { pagination =>
        assert(pagination.omitLeft == false)
        assert(pagination.omitRight == true)
      }
      defining(Pagination(9, 100, 10, 6)) { pagination =>
        assert(pagination.omitLeft == true)
        assert(pagination.omitRight == false)
      }
    }
  }

  describe("visibleFor") {
    it("should return true for visible pagination links") {
      defining(Pagination(1, 100, 10, 6)) { pagination =>
        assert(pagination.visibleFor(1) == true)
        assert(pagination.visibleFor(2) == true)
        assert(pagination.visibleFor(3) == true)
        assert(pagination.visibleFor(4) == true)
        assert(pagination.visibleFor(5) == true)
        assert(pagination.visibleFor(6) == false)
        assert(pagination.visibleFor(7) == false)
        assert(pagination.visibleFor(8) == false)
        assert(pagination.visibleFor(9) == false)
        assert(pagination.visibleFor(10) == true)
      }
      defining(Pagination(5, 100, 10, 6)) { pagination =>
        assert(pagination.visibleFor(1) == true)
        assert(pagination.visibleFor(2) == false)
        assert(pagination.visibleFor(3) == false)
        assert(pagination.visibleFor(4) == true)
        assert(pagination.visibleFor(5) == true)
        assert(pagination.visibleFor(6) == true)
        assert(pagination.visibleFor(7) == false)
        assert(pagination.visibleFor(8) == false)
        assert(pagination.visibleFor(9) == false)
        assert(pagination.visibleFor(10) == true)
      }
      defining(Pagination(8, 100, 10, 6)) { pagination =>
        assert(pagination.visibleFor(1) == true)
        assert(pagination.visibleFor(2) == false)
        assert(pagination.visibleFor(3) == false)
        assert(pagination.visibleFor(4) == false)
        assert(pagination.visibleFor(5) == false)
        assert(pagination.visibleFor(6) == true)
        assert(pagination.visibleFor(7) == true)
        assert(pagination.visibleFor(8) == true)
        assert(pagination.visibleFor(9) == true)
        assert(pagination.visibleFor(10) == true)
      }
    }
  }

}
