package gitbucket.core.util

import GitSpecUtil._
import org.scalatest.FunSuite

class JGitUtilSpec extends FunSuite {

  test("getFileList(git: Git, revision: String, path)"){
    withTestRepository { git =>
      def list(branch: String, path: String) =
        JGitUtil.getFileList(git, branch, path).map(finfo => (finfo.name, finfo.message, finfo.isDirectory))
      assert(list("master", ".") == Nil)
      assert(list("master", "dir/subdir") == Nil)
      assert(list("branch", ".") == Nil)
      assert(list("branch", "dir/subdir") == Nil)

      createFile(git, "master", "README.md", "body1", message = "commit1")

      assert(list("master", ".") == List(("README.md", "commit1", false)))
      assert(list("master", "dir/subdir") == Nil)
      assert(list("branch", ".") == Nil)
      assert(list("branch", "dir/subdir") == Nil)

      createFile(git, "master", "README.md", "body2", message = "commit2")

      assert(list("master", ".") == List(("README.md", "commit2", false)))
      assert(list("master", "dir/subdir") == Nil)
      assert(list("branch", ".") == Nil)
      assert(list("branch", "dir/subdir") == Nil)

      createFile(git, "master", "dir/subdir/File3.md", "body3", message = "commit3")

      assert(list("master", ".") == List(("dir/subdir", "commit3", true), ("README.md", "commit2", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit3", false)))
      assert(list("branch", ".") == Nil)
      assert(list("branch", "dir/subdir") == Nil)

      createFile(git, "master", "dir/subdir/File4.md", "body4", message = "commit4")

      assert(list("master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit2", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("branch", ".") == Nil)
      assert(list("branch", "dir/subdir") == Nil)

      createFile(git, "master", "README5.md", "body5", message = "commit5")

      assert(list("master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit2", false), ("README5.md", "commit5", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("branch", ".") == Nil)
      assert(list("branch", "dir/subdir") == Nil)

      createFile(git, "master", "README.md", "body6", message = "commit6")

      assert(list("master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("branch", ".") == Nil)
      assert(list("branch", "dir/subdir") == Nil)

      git.branchCreate().setName("branch").setStartPoint("master").call()

      assert(list("master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("branch", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("branch", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))

      createFile(git, "branch", "dir/subdir/File3.md", "body7", message = "commit7")

      assert(list("master", ".") == List(("dir/subdir", "commit4", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("branch", ".") == List(("dir/subdir", "commit7", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("branch", "dir/subdir") == List(("File3.md", "commit7", false), ("File4.md", "commit4", false)))

      createFile(git, "master", "dir8/File8.md", "body8", message = "commit8")

      assert(list("master", ".") == List(("dir/subdir", "commit4", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("branch", ".") == List(("dir/subdir", "commit7", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("branch", "dir/subdir") == List(("File3.md", "commit7", false), ("File4.md", "commit4", false)))

      createFile(git, "branch", "dir/subdir9/File9.md", "body9", message = "commit9")

      assert(list("master", ".") == List(("dir/subdir", "commit4", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit3", false), ("File4.md", "commit4", false)))
      assert(list("branch", ".") == List(("dir", "commit9", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("branch", "dir/subdir") == List(("File3.md", "commit7", false), ("File4.md", "commit4", false)))

      mergeAndCommit(git, "master", "branch", message = "merge10")

      assert(list("master", ".") == List(("dir", "commit9", true), ("dir8", "commit8", true), ("README.md", "commit6", false), ("README5.md", "commit5", false)))
      assert(list("master", "dir/subdir") == List(("File3.md", "commit7", false), ("File4.md", "commit4", false)))
    }
  }

  test("getFileList subfolder multi-origin (issue #721)") {
    withTestRepository { git =>
      def list(branch: String, path: String) =
        JGitUtil.getFileList(git, branch, path).map(finfo => (finfo.name, finfo.message, finfo.isDirectory))
      createFile(git, "master", "README.md", "body1", message = "commit1")
      createFile(git, "branch", "test/text2.txt", "body2", message = "commit2")
      mergeAndCommit(git, "master", "branch", message = "merge3")
      assert(list("master", "test") == List(("text2.txt", "commit2", false)))
    }
  }
}
