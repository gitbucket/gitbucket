package gitbucket.core.model

import gitbucket.core.model.CommitState._
import org.scalatest.funspec.AnyFunSpec

class CommitStateSpec extends AnyFunSpec {
  describe("CommitState") {
    it("should combine empty must eq PENDING") {
      assert(combine(Set()) == PENDING)
    }
    it("should combine includes ERROR must eq FAILURE") {
      assert(combine(Set(ERROR, SUCCESS, PENDING)) == FAILURE)
    }
    it("should combine includes FAILURE must eq peinding") {
      assert(combine(Set(FAILURE, SUCCESS, PENDING)) == FAILURE)
    }
    it("should combine includes PENDING must eq peinding") {
      assert(combine(Set(PENDING, SUCCESS)) == PENDING)
    }
    it("should combine only SUCCESS must eq SUCCESS") {
      assert(combine(Set(SUCCESS)) == SUCCESS)
    }
  }
}
