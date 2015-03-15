package model

import org.specs2.mutable.Specification

import CommitState._

class CommitStateSpec extends Specification {
  "CommitState" should {
    "combine empty must eq PENDING" in {
      combine(Set()) must_== PENDING
    }
    "combine includes ERROR must eq FAILURE" in {
      combine(Set(ERROR, SUCCESS, PENDING)) must_== FAILURE
    }
    "combine includes FAILURE must eq peinding" in {
      combine(Set(FAILURE, SUCCESS, PENDING)) must_== FAILURE
    }
    "combine includes PENDING must eq peinding" in {
      combine(Set(PENDING, SUCCESS)) must_== PENDING
    }
    "combine only SUCCESS must eq SUCCESS" in {
      combine(Set(SUCCESS)) must_== SUCCESS
    }
  }
}
