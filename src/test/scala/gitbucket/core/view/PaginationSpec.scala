package gitbucket.core.view

import gitbucket.core.util.ControlUtil
import org.specs2.mutable._
import ControlUtil._

class PaginationSpec extends Specification {

  "max" should {
    "return max page number" in {
      val pagination = Pagination(1, 100, 10, 6)
      pagination.max mustEqual 10
    }
  }

  "omitLeft and omitRight" should {
    "return true if pagination links at their side will be omitted" in {
      defining(Pagination(1, 100, 10, 6)){ pagination =>
        pagination.omitLeft mustEqual false
        pagination.omitRight mustEqual true
      }
      defining(Pagination(9, 100, 10, 6)){ pagination =>
        pagination.omitLeft mustEqual true
        pagination.omitRight mustEqual false
      }
    }
  }

  "visibleFor" should {
    "return true for visible pagination links" in {
      defining(Pagination(1, 100, 10, 6)){ pagination =>
        pagination.visibleFor(1) mustEqual true
        pagination.visibleFor(2) mustEqual true
        pagination.visibleFor(3) mustEqual true
        pagination.visibleFor(4) mustEqual true
        pagination.visibleFor(5) mustEqual true
        pagination.visibleFor(6) mustEqual false
        pagination.visibleFor(7) mustEqual false
        pagination.visibleFor(8) mustEqual false
        pagination.visibleFor(9) mustEqual false
        pagination.visibleFor(10) mustEqual true
      }
      defining(Pagination(5, 100, 10, 6)){ pagination =>
        pagination.visibleFor(1) mustEqual true
        pagination.visibleFor(2) mustEqual false
        pagination.visibleFor(3) mustEqual false
        pagination.visibleFor(4) mustEqual true
        pagination.visibleFor(5) mustEqual true
        pagination.visibleFor(6) mustEqual true
        pagination.visibleFor(7) mustEqual false
        pagination.visibleFor(8) mustEqual false
        pagination.visibleFor(9) mustEqual false
        pagination.visibleFor(10) mustEqual true
      }
      defining(Pagination(8, 100, 10, 6)){ pagination =>
        pagination.visibleFor(1) mustEqual true
        pagination.visibleFor(2) mustEqual false
        pagination.visibleFor(3) mustEqual false
        pagination.visibleFor(4) mustEqual false
        pagination.visibleFor(5) mustEqual false
        pagination.visibleFor(6) mustEqual true
        pagination.visibleFor(7) mustEqual true
        pagination.visibleFor(8) mustEqual true
        pagination.visibleFor(9) mustEqual true
        pagination.visibleFor(10) mustEqual true
      }
    }
  }

}
