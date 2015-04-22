package gitbucket.core.util

import org.specs2.mutable._
import GitSpecUtil._

class JGitUtilSpec extends Specification {

  "getFileList(git: Git, revision: String, path)" should {
    withTestRepository { git =>
      def list(branch: String, path: String) =
        JGitUtil.getFileList(git, branch, path).map(finfo => (finfo.name, finfo.message, finfo.isDirectory))
      list("master", ".") mustEqual Nil
      list("master", "dir/subdir") mustEqual Nil
      list("branch", ".") mustEqual Nil
      list("branch", "dir/subdir") mustEqual Nil

      createFile(git, "master", "README.md", "body1", message = "commit1")

      list("master", ".") mustEqual List(("README.md", "commit1", false))
      list("master", "dir/subdir") mustEqual Nil
      list("branch", ".") mustEqual Nil
      list("branch", "dir/subdir") mustEqual Nil

      createFile(git, "master", "README.md", "body2", message = "commit2")

      list("master", ".") mustEqual List(("README.md", "commit2", false))
      list("master", "dir/subdir") mustEqual Nil
      list("branch", ".") mustEqual Nil
      list("branch", "dir/subdir") mustEqual Nil

      createFile(git, "master", "dir/subdir/File3.md", "body3", message = "commit3")

      list("master", ".") mustEqual List(("dir/subdir", "commit3", true), ("README.md", "commit2", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit3", false))
      list("branch", ".") mustEqual Nil
      list("branch", "dir/subdir") mustEqual Nil

      createFile(git, "master", "dir/subdir/File4.md", "body4", message = "commit4")

      list("master", ".") mustEqual List(("dir/subdir", "commit4", true), ("README.md", "commit2", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit3", false), ("File4.md", "commit4", false))
      list("branch", ".") mustEqual Nil
      list("branch", "dir/subdir") mustEqual Nil

      createFile(git, "master", "README5.md", "body5", message = "commit5")

      list("master", ".") mustEqual List(("dir/subdir", "commit4", true), ("README.md", "commit2", false), ("README5.md", "commit5", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit3", false), ("File4.md", "commit4", false))
      list("branch", ".") mustEqual Nil
      list("branch", "dir/subdir") mustEqual Nil

      createFile(git, "master", "README.md", "body6", message = "commit6")

      list("master", ".") mustEqual List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit3", false), ("File4.md", "commit4", false))
      list("branch", ".") mustEqual Nil
      list("branch", "dir/subdir") mustEqual Nil

      git.branchCreate().setName("branch").setStartPoint("master").call()

      list("master", ".") mustEqual List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit3", false), ("File4.md", "commit4", false))
      list("branch", ".") mustEqual List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("branch", "dir/subdir") mustEqual List(("File3.md", "commit3", false), ("File4.md", "commit4", false))

      createFile(git, "branch", "dir/subdir/File3.md", "body7", message = "commit7")

      list("master", ".") mustEqual List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit3", false), ("File4.md", "commit4", false))
      list("branch", ".") mustEqual List(("dir/subdir", "commit7", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("branch", "dir/subdir") mustEqual List(("File3.md", "commit7", false), ("File4.md", "commit4", false))

      createFile(git, "master", "dir8/File8.md", "body8", message = "commit8")

      list("master", ".") mustEqual List(("dir/subdir", "commit4", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit3", false), ("File4.md", "commit4", false))
      list("branch", ".") mustEqual List(("dir/subdir", "commit7", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("branch", "dir/subdir") mustEqual List(("File3.md", "commit7", false), ("File4.md", "commit4", false))

      createFile(git, "branch", "dir/subdir9/File9.md", "body9", message = "commit9")

      list("master", ".") mustEqual List(("dir/subdir", "commit4", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit3", false), ("File4.md", "commit4", false))
      list("branch", ".") mustEqual List(("dir", "commit9", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("branch", "dir/subdir") mustEqual List(("File3.md", "commit7", false), ("File4.md", "commit4", false))

      mergeAndCommit(git, "master", "branch", message = "merge10")

      list("master", ".") mustEqual List(("dir", "commit9", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false))
      list("master", "dir/subdir") mustEqual List(("File3.md", "commit7", false), ("File4.md", "commit4", false))
    }
  }
  "getFileList subfolder multi-origin (issue #721)" should {
    withTestRepository { git =>
      def list(branch: String, path: String) =
        JGitUtil.getFileList(git, branch, path).map(finfo => (finfo.name, finfo.message, finfo.isDirectory))
      createFile(git, "master", "README.md", "body1", message = "commit1")
      createFile(git, "branch", "test/text2.txt", "body2", message = "commit2")
      mergeAndCommit(git, "master", "branch", message = "merge3")
      list("master", "test") mustEqual List(("text2.txt", "commit2", false))
    }
  }
}
