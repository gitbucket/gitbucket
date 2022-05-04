package gitbucket.core.model

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.StringUtil
import gitbucket.core.view.helpers
import org.scalatra.i18n.Messages

trait CustomFieldComponent extends TemplateComponent { self: Profile =>
  import profile.api._

  lazy val CustomFields = TableQuery[CustomFields]

  class CustomFields(tag: Tag) extends Table[CustomField](tag, "CUSTOM_FIELD") with BasicTemplate {
    val fieldId = column[Int]("FIELD_ID", O AutoInc)
    val fieldName = column[String]("FIELD_NAME")
    val fieldType = column[String]("FIELD_TYPE")
    val enableForIssues = column[Boolean]("ENABLE_FOR_ISSUES")
    val enableForPullRequests = column[Boolean]("ENABLE_FOR_PULL_REQUESTS")
    def * =
      (userName, repositoryName, fieldId, fieldName, fieldType, enableForIssues, enableForPullRequests)
        .<>(CustomField.tupled, CustomField.unapply)

    def byPrimaryKey(userName: String, repositoryName: String, fieldId: Int) =
      (this.userName === userName.bind) && (this.repositoryName === repositoryName.bind) && (this.fieldId === fieldId.bind)
  }
}

case class CustomField(
  userName: String,
  repositoryName: String,
  fieldId: Int = 0,
  fieldName: String,
  fieldType: String, // long, double, string, or date
  enableForIssues: Boolean,
  enableForPullRequests: Boolean
)

trait CustomFieldBehavior {
  def createHtml(repository: RepositoryInfo, fieldId: Int)(implicit conext: Context): String
  def fieldHtml(repository: RepositoryInfo, issueId: Int, fieldId: Int, value: String, editable: Boolean)(
    implicit context: Context
  ): String
  def validate(name: String, value: String, messages: Messages): Option[String]
}

object CustomFieldBehavior {
  def validate(field: CustomField, value: String, messages: Messages): Option[String] = {
    if (value.isEmpty) None
    else {
      CustomFieldBehavior(field.fieldType).flatMap { behavior =>
        behavior.validate(field.fieldName, value, messages)
      }
    }
  }

  def apply(fieldType: String): Option[CustomFieldBehavior] = {
    fieldType match {
      case "long"   => Some(LongFieldBehavior)
      case "double" => Some(DoubleFieldBehavior)
      case "string" => Some(StringFieldBehavior)
      case "date"   => Some(DateFieldBehavior)
      case _        => None
    }
  }

  case object LongFieldBehavior extends TextFieldBehavior {
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      try {
        value.toLong
        None
      } catch {
        case _: NumberFormatException => Some(messages("error.number").format(name))
      }
    }
  }
  case object DoubleFieldBehavior extends TextFieldBehavior {
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      try {
        value.toDouble
        None
      } catch {
        case _: NumberFormatException => Some(messages("error.number").format(name))
      }
    }
  }
  case object StringFieldBehavior extends TextFieldBehavior
  case object DateFieldBehavior extends TextFieldBehavior {
    private val pattern = "yyyy-MM-dd"
    override protected val fieldType: String = "date"

    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      try {
        new java.text.SimpleDateFormat(pattern).parse(value)
        None
      } catch {
        case _: java.text.ParseException =>
          Some(messages("error.datePattern").format(name, pattern))
      }
    }
  }

  trait TextFieldBehavior extends CustomFieldBehavior {
    protected val fieldType = "text"

    def createHtml(repository: RepositoryInfo, fieldId: Int)(implicit context: Context): String = {
      val sb = new StringBuilder
      sb.append(
        s"""<input type="$fieldType" class="form-control input-sm" id="custom-field-$fieldId" name="custom-field-$fieldId" data-field-id="$fieldId" style="width: 120px;"/>"""
      )
      sb.append(s"""<script>
                   |$$('#custom-field-$fieldId').focusout(function(){
                   |  const $$this = $$(this);
                   |  const fieldId = $$this.data('field-id');
                   |  $$.post('${helpers.url(repository)}/issues/customfield_validation/' + fieldId,
                   |    { value: $$this.val() },
                   |    function(data){
                   |      if (data != '') {
                   |        $$('#custom-field-$fieldId-error').text(data);
                   |      } else {
                   |        $$('#custom-field-$fieldId-error').text('');
                   |      }
                   |    }
                   |  );
                   |});
                   |</script>
                   |""".stripMargin)
      sb.toString()
    }

    def fieldHtml(repository: RepositoryInfo, issueId: Int, fieldId: Int, value: String, editable: Boolean)(
      implicit context: Context
    ): String = {
      val sb = new StringBuilder
      sb.append(
        s"""<span id="custom-field-$fieldId-label" class="custom-field-label">${StringUtil
             .escapeHtml(value)}</span>""".stripMargin
      )
      if (editable) {
        sb.append(
          s"""<input type="$fieldType" id="custom-field-$fieldId-editor" class="form-control input-sm custom-field-editor" data-field-id="$fieldId" style="width: 120px; display: none;"/>"""
        )
        sb.append(s"""<script>
            |$$('#custom-field-$fieldId-label').click(function(){
            |  const $$this = $$(this);
            |  $$this.hide();
            |  $$this.next().val($$this.text()).show().focus();
            |});
            |
            |$$('#custom-field-$fieldId-editor').focusout(function(){
            |  const $$this = $$(this);
            |  const fieldId = $$this.data('field-id');
            |  $$.post('${helpers.url(repository)}/issues/customfield_validation/' + fieldId,
            |    { value: $$this.val() },
            |    function(data){
            |      if (data != '') {
            |        $$('#custom-field-$fieldId-error').text(data);
            |      } else {
            |        $$('#custom-field-$fieldId-error').text('');
            |        $$.post('${helpers.url(repository)}/issues/$issueId/customfield/' + fieldId,
            |          { value: $$this.val() },
            |          function(data){
            |            $$this.hide();
            |            $$this.prev().text(data).show();
            |          }
            |        );
            |      }
            |    }
            |  );
            |});
            |
            |// ESC key handling in text field
            |$$('#custom-field-$fieldId-editor').keyup(function(e){
            |  if (e.keyCode == 27) {
            |    const $$this = $$(this);
            |    $$this.hide();
            |    $$this.prev().show();
            |  }
            |  if (e.keyCode == 13) {
            |    $$('#custom-field-$fieldId-editor').blur();
            |  }
            |});
            |</script>
            |""".stripMargin)
      }
      sb.toString()
    }

    def validate(name: String, value: String, messages: Messages): Option[String] = None
  }
}
