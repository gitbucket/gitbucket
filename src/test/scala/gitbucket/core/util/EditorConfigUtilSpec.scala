package gitbucket.core.util

import org.scalatest.FunSuite
import GitSpecUtil._

class EditorConfigUtilSpec extends FunSuite {
  val simpleConfig =
    """[*.txt]
      |indent_style = tab
      |indent_size = 4""".stripMargin

  test("no EditorConfig file") {
    withTestRepository { git =>
      createFile(git, "master", "README.md", "body", message = "commit1")
      val props = EditorConfigUtil.readProperties(git, "master", "test.txt")
      assert(EditorConfigUtil.getTabWidth(props) == 8)
      assert(EditorConfigUtil.getUseSoftTabs(props) == false)
      assert(EditorConfigUtil.getNewLineMode(props) == "auto")

      val subdirProps = EditorConfigUtil.readProperties(git, "master", "dir1/dir2/dir3/dir4/test.txt")
      assert(EditorConfigUtil.getTabWidth(subdirProps) == 8)
      assert(EditorConfigUtil.getUseSoftTabs(subdirProps) == false)
      assert(EditorConfigUtil.getNewLineMode(subdirProps) == "auto")
    }
  }

  test("simple EditorConfig") {
    withTestRepository { git =>
      createFile(git, "master", ".editorconfig", simpleConfig, message = "commit1")

      val props = EditorConfigUtil.readProperties(git, "master", "test.txt")
      assert(EditorConfigUtil.getTabWidth(props) == 4)

      val subdirProps = EditorConfigUtil.readProperties(git, "master", "dir1/dir2/dir3/dir4/test.txt")
      assert(EditorConfigUtil.getTabWidth(subdirProps) == 4)
    }
  }
}
