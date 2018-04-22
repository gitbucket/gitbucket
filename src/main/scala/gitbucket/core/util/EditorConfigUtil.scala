package gitbucket.core.util

import java.io.IOException

import editorconfig.{JGitResource, JGitResourcePath}
import org.ec4j.core.model.PropertyType.{EndOfLineValue, IndentStyleValue}
import org.ec4j.core.model.{PropertyType, Version}
import org.ec4j.core.parser.ParseException
import org.ec4j.core.{EditorConfigConstants, EditorConfigLoader, ResourceProperties, ResourcePropertiesService}
import org.eclipse.jgit.api.Git

object EditorConfigUtil {
  val TabSizeDefault: Int = 8
  val NewLineModeDefault: String = "auto"
  val UseSoftTabsDefault = false

  case class EditorConfigInfo(
    tabSize: Int,
    newLineMode: String,
    useSoftTabs: Boolean
  )

  def getEditorConfigInfo(git: Git, rev: String, path: String): EditorConfigInfo = {
    try {
      val resourcePropertiesService = ResourcePropertiesService
        .builder()
        .configFileName(EditorConfigConstants.EDITORCONFIG)
        .rootDirectory(JGitResourcePath.RootDirectory(git, rev))
        .loader(EditorConfigLoader.of(Version.CURRENT))
        .keepUnset(true)
        .build()

      val props = resourcePropertiesService.queryProperties(new JGitResource(git, rev, path))
      EditorConfigInfo(
        tabSize = props.getValue[Integer](PropertyType.tab_width, TabSizeDefault, false),
        newLineMode = props.getValue[EndOfLineValue](PropertyType.end_of_line, null, false) match {
          case EndOfLineValue.cr   => "cr"
          case EndOfLineValue.lf   => "lf"
          case EndOfLineValue.crlf => "crlf"
          case _                   => "auto"
        },
        props.getValue[IndentStyleValue](PropertyType.indent_style, null, false) match {
          case IndentStyleValue.space => true
          case IndentStyleValue.tab   => false
          case _                      => false
        }
      )
    } catch {
      case e: ParseException => EditorConfigInfo(TabSizeDefault, NewLineModeDefault, UseSoftTabsDefault)
      case e: IOException    => EditorConfigInfo(TabSizeDefault, NewLineModeDefault, UseSoftTabsDefault)
    }
  }
}
