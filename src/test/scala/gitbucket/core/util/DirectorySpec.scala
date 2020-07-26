package gitbucket.core.util

import org.scalatest.funspec.AnyFunSpec

class DirectorySpec extends AnyFunSpec {

  describe("GitBucketHome") {
    it("should set under target in test scope") {
      assert(Directory.GitBucketHome == new java.io.File("target/gitbucket_home_for_test").getAbsolutePath)
    }
  }
//  test("GitBucketHome should exists"){
//    new java.io.File(Directory.GitBucketHome).exists
//  }

}
