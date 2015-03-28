package gitbucket.core.util

import org.specs2.mutable._


class DirectorySpec extends Specification {
  "GitBucketHome" should {
    "set under target in test scope" in {
      Directory.GitBucketHome mustEqual new java.io.File("target/gitbucket_home_for_test").getAbsolutePath
    }
    "exists" in {
      new java.io.File(Directory.GitBucketHome).exists
    }
  }
}
