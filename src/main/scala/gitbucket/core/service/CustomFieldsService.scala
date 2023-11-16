package gitbucket.core.service

import gitbucket.core.model.{CustomField, IssueCustomField}
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._

trait CustomFieldsService {

  def getCustomFields(owner: String, repository: String)(implicit s: Session): List[CustomField] =
    CustomFields.filter(_.byRepository(owner, repository)).sortBy(_.fieldId asc).list

  def getCustomFieldsWithValue(owner: String, repository: String, issueId: Int)(implicit
    s: Session
  ): List[(CustomField, Option[IssueCustomField])] = {
    CustomFields
      .filter(_.byRepository(owner, repository))
      .joinLeft(IssueCustomFields)
      .on { case (t1, t2) => t1.fieldId === t2.fieldId && t2.issueId === issueId.bind }
      .sortBy { case (t1, t2) => t1.fieldId }
      .list
  }

  def getCustomField(owner: String, repository: String, fieldId: Int)(implicit s: Session): Option[CustomField] =
    CustomFields.filter(_.byPrimaryKey(owner, repository, fieldId)).firstOption

  def createCustomField(
    owner: String,
    repository: String,
    fieldName: String,
    fieldType: String,
    constraints: Option[String],
    enableForIssues: Boolean,
    enableForPullRequests: Boolean
  )(implicit s: Session): Int = {
    CustomFields returning CustomFields.map(_.fieldId) insert CustomField(
      userName = owner,
      repositoryName = repository,
      fieldName = fieldName,
      fieldType = fieldType,
      constraints = constraints,
      enableForIssues = enableForIssues,
      enableForPullRequests = enableForPullRequests
    )
  }

  def updateCustomField(
    owner: String,
    repository: String,
    fieldId: Int,
    fieldName: String,
    fieldType: String,
    constraints: Option[String],
    enableForIssues: Boolean,
    enableForPullRequests: Boolean
  )(implicit
    s: Session
  ): Unit =
    CustomFields
      .filter(_.byPrimaryKey(owner, repository, fieldId))
      .map(t => (t.fieldName, t.fieldType, t.constraints, t.enableForIssues, t.enableForPullRequests))
      .update((fieldName, fieldType, constraints, enableForIssues, enableForPullRequests))

  def deleteCustomField(owner: String, repository: String, fieldId: Int)(implicit s: Session): Unit = {
    IssueCustomFields
      .filter(t => t.userName === owner.bind && t.repositoryName === repository.bind && t.fieldId === fieldId.bind)
      .delete
    CustomFields.filter(_.byPrimaryKey(owner, repository, fieldId)).delete
  }

  def getCustomFieldValues(
    userName: String,
    repositoryName: String,
    issueId: Int,
  )(implicit s: Session): List[IssueCustomField] = {
    IssueCustomFields
      .filter(t => t.userName === userName && t.repositoryName === repositoryName.bind && t.issueId === issueId.bind)
      .list
  }

  def insertOrUpdateCustomFieldValue(
    field: CustomField,
    userName: String,
    repositoryName: String,
    issueId: Int,
    value: String
  )(implicit s: Session): Unit = {
    IssueCustomFields.insertOrUpdate(
      IssueCustomField(
        userName = userName,
        repositoryName = repositoryName,
        issueId = issueId,
        fieldId = field.fieldId,
        value = value
      )
    )
  }
}
