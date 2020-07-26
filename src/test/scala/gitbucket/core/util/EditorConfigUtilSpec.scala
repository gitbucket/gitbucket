package gitbucket.core.util

import org.scalatest.funsuite.AnyFunSuite
import GitSpecUtil._

class EditorConfigUtilSpec extends AnyFunSuite {
  val simpleConfig =
    """[*.txt]
      |indent_style = tab
      |indent_size = 4""".stripMargin

  test("no EditorConfig file") {
    withTestRepository { git =>
      createFile(git, "master", "README.md", "body", message = "commit1")
      val info = EditorConfigUtil.getEditorConfigInfo(git, "master", "test.txt")
      assert(info.tabSize == 8)
      assert(info.useSoftTabs == false)
      assert(info.newLineMode == "auto")

      val subdirInfo = EditorConfigUtil.getEditorConfigInfo(git, "master", "dir1/dir2/dir3/dir4/test.txt")
      assert(subdirInfo.tabSize == 8)
      assert(subdirInfo.useSoftTabs == false)
      assert(subdirInfo.newLineMode == "auto")
    }
  }

  test("simple EditorConfig") {
    withTestRepository { git =>
      createFile(git, "master", ".editorconfig", simpleConfig, message = "commit1")

      val info = EditorConfigUtil.getEditorConfigInfo(git, "master", "test.txt")
      assert(info.tabSize == 4)

      val subdirInfo = EditorConfigUtil.getEditorConfigInfo(git, "master", "dir1/dir2/dir3/dir4/test.txt")
      assert(subdirInfo.tabSize == 4)
    }
  }

  test(".editorconfig parse error") {
    withTestRepository { git =>
      createFile(git, "master", ".editorconfig", "equal_missing", message = "commit1")

      val info = EditorConfigUtil.getEditorConfigInfo(git, "master", "test.txt")
      assert(info.tabSize == 8)
      assert(info.useSoftTabs == false)
      assert(info.newLineMode == "auto")
    }
  }
}
