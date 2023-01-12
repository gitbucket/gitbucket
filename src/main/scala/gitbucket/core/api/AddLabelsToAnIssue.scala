package gitbucket.core.api

/**
 * https://developer.github.com/v3/issues/labels/#add-labels-to-an-issue
 */
case class AddLabelsToAnIssue(labels: Seq[String])
