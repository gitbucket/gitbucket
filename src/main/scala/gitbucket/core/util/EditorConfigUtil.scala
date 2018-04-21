package gitbucket.core.util

import java.nio.charset.StandardCharsets

import editorconfig.{JGitResource, JGitResourcePath}
import org.ec4j.core.model.PropertyType.{EndOfLineValue, IndentStyleValue}
import org.ec4j.core.model.{PropertyType, Version}
import org.ec4j.core.{EditorConfigConstants, EditorConfigLoader, ResourceProperties, ResourcePropertiesService}
import org.eclipse.jgit.api.Git

import collection.JavaConverters._

object EditorConfigUtil {
  def readProperties(git: Git, rev: String, path: String): ResourceProperties = {
    val resourcePropertiesService = ResourcePropertiesService
      .builder()
      .configFileName(EditorConfigConstants.EDITORCONFIG)
      .rootDirectory(JGitResourcePath.RootDirectory(git, rev))
      .loader(EditorConfigLoader.of(Version.CURRENT))
      .keepUnset(true)
      .build()

    resourcePropertiesService.queryProperties(new JGitResource(git, rev, path))
  }

  def getTabWidth(props: ResourceProperties): Int = {
    props.getValue[Integer](PropertyType.tab_width, 8, false)
  }

  def getNewLineMode(props: ResourceProperties): String = {
    props.getValue[EndOfLineValue](PropertyType.end_of_line, null, false) match {
      case EndOfLineValue.cr   => "cr"
      case EndOfLineValue.lf   => "lf"
      case EndOfLineValue.crlf => "crlf"
      case _                   => "auto"
    }
  }

  def getUseSoftTabs(props: ResourceProperties): Boolean = {
    props.getValue[IndentStyleValue](PropertyType.indent_style, IndentStyleValue.tab, false) match {
      case IndentStyleValue.space => true
      case IndentStyleValue.tab   => false
      case _                      => false
    }
  }
}
