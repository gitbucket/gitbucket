package gitbucket.core.util

import GitSpecUtil._
import org.scalatest.FunSuite

class JGitUtilSpec extends FunSuite {

  test("getFileList(git: Git, revision: String, path)"){
    withTestRepository { git =>
      def list(branch: String, path: String) =
        JGitUtil.getFileList(git, branch, path).map(finfo => (finfo.name, finfo.message, finfo.isDirectory))
      assert(list("refs/heads/master", ".") == Nil)
      assert(list("refs/heads/master", "dir/subdir") == Nil)
      assert(list("refs/heads/branch", ".") == Nil)
      assert(list("refs/heads/branch", "dir/subdir") == Nil)

      createFile(git, "refs/heads/master", "README.md", "body1", message = "commit1")

      assert(list("refs/heads/master", ".") == List(("README.md", "commit1", false)))
      assert(list("refs/heads/master", "dir/subdir") == Nil)
      assert(list("refs/heads/branch", ".") == Nil)
      assert(list("refs/heads/branch", "dir/subdir") == Nil)

      createFile(git, "refs/heads/master", "README.md", "body2", message = "commit2")

      assert(list("refs/heads/master", ".") == List(("README.md", "commit2", false)))
      assert(list("refs/heads/master", "dir/subdir") == Nil)
      assert(list("refs/heads/branch", ".") == Nil)
      assert(list("refs/heads/branch", "dir/subdir") == Nil)

      createFile(git, "refs/heads/master", "dir/subdir/File3.md", "body3", message = "commit3")

      assert(list("refs/heads/master", ".") == List(("dir/subdir", "commit3", true), ("README.md", "commit2", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit3", false)))
      assert(list("refs/heads/branch", ".") == Nil)
      assert(list("refs/heads/branch", "dir/subdir") == Nil)

      createFile(git, "refs/heads/master", "dir/subdir/File4.md", "body4", message = "commit4")

      assert(list("refs/heads/master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit2", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("refs/heads/branch", ".") == Nil)
      assert(list("refs/heads/branch", "dir/subdir") == Nil)

      createFile(git, "refs/heads/master", "README5.md", "body5", message = "commit5")

      assert(list("refs/heads/master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit2", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("refs/heads/branch", ".") == Nil)
      assert(list("refs/heads/branch", "dir/subdir") == Nil)

      createFile(git, "refs/heads/master", "README.md", "body6", message = "commit6")

      assert(list("refs/heads/master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("refs/heads/branch", ".") == Nil)
      assert(list("refs/heads/branch", "dir/subdir") == Nil)

      git.branchCreate().setName("refs/heads/branch").setStartPoint("master").call()

      assert(list("refs/heads/master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("refs/heads/branch", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/branch", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))

      createFile(git, "refs/heads/branch", "dir/subdir/File3.md", "body7", message = "commit7")

      assert(list("refs/heads/master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("refs/heads/branch", ".") == List(("dir/subdir", "commit7", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/branch", "dir/subdir") == List(("File3.md", "commit7", false), ("File4.md", "commit4", false)))

      createFile(git, "refs/heads/master", "dir8/File8.md", "body8", message = "commit8")

      assert(list("refs/heads/master", ".") == List(("dir/subdir", "commit4", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("refs/heads/branch", ".") == List(("dir/subdir", "commit7", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/branch", "dir/subdir") == List(("File3.md", "commit7", false), ("File4.md", "commit4", false)))

      createFile(git, "refs/heads/branch", "dir/subdir9/File9.md", "body9", message = "commit9")

      assert(list("refs/heads/master", ".") == List(("dir/subdir", "commit4", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("refs/heads/branch", ".") == List(("dir", "commit9", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/branch", "dir/subdir") == List(("File3.md", "commit7", false), ("File4.md", "commit4", false)))

      mergeAndCommit(git, "refs/heads/master", "refs/heads/branch", message = "merge10")

      assert(list("refs/heads/master", ".") == List(("dir", "commit9", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("refs/heads/master", "dir/subdir") == List(("File3.md", "commit7", false), ("File4.md", "commit4", false)))
    }
  }

  test("getFileList subfolder multi-origin (issue #721)") {
    withTestRepository { git =>
      def list(branch: String, path: String) =
        JGitUtil.getFileList(git, branch, path).map(finfo => (finfo.name, finfo.message, finfo.isDirectory))
      createFile(git, "refs/heads/master", "README.md", "body1", message = "commit1")
      createFile(git, "refs/heads/branch", "test/text2.txt", "body2", message = "commit2")
      mergeAndCommit(git, "refs/heads/master", "refs/heads/branch", message = "merge3")
      assert(list("refs/heads/master", "test") == List(("text2.txt", "commit2", false)))
    }
  }
}
