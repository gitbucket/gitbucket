package gitbucket.core.model

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.StringUtil
import gitbucket.core.view.helpers
import org.scalatra.i18n.Messages
import play.twirl.api.Html

trait CustomFieldComponent extends TemplateComponent { self: Profile =>
  import profile.api._

  lazy val CustomFields = TableQuery[CustomFields]

  class CustomFields(tag: Tag) extends Table[CustomField](tag, "CUSTOM_FIELD") with BasicTemplate {
    val fieldId = column[Int]("FIELD_ID", O AutoInc)
    val fieldName = column[String]("FIELD_NAME")
    val fieldType = column[String]("FIELD_TYPE")
    val constraints = column[Option[String]]("CONSTRAINTS")
    val enableForIssues = column[Boolean]("ENABLE_FOR_ISSUES")
    val enableForPullRequests = column[Boolean]("ENABLE_FOR_PULL_REQUESTS")
    def * =
      (userName, repositoryName, fieldId, fieldName, fieldType, constraints, enableForIssues, enableForPullRequests)
        .mapTo[CustomField]

    def byPrimaryKey(userName: String, repositoryName: String, fieldId: Int) =
      (this.userName === userName.bind) && (this.repositoryName === repositoryName.bind) && (this.fieldId === fieldId.bind)
  }
}

case class CustomField(
  userName: String,
  repositoryName: String,
  fieldId: Int = 0,
  fieldName: String,
  fieldType: String, // long, double, string, date, or enum
  constraints: Option[String],
  enableForIssues: Boolean,
  enableForPullRequests: Boolean
)

trait CustomFieldBehavior {
  def createHtml(repository: RepositoryInfo, fieldId: Int, fieldName: String, constraints: Option[String])(
    implicit context: Context
  ): String
  def fieldHtml(
    repository: RepositoryInfo,
    issueId: Int,
    fieldId: Int,
    fieldName: String,
    constraints: Option[String],
    value: String,
    editable: Boolean
  )(
    implicit context: Context
  ): String
  def validate(name: String, constraints: Option[String], value: String, messages: Messages): Option[String]
}

object CustomFieldBehavior {
  def validate(field: CustomField, value: String, messages: Messages): Option[String] = {
    if (value.isEmpty) None
    else {
      CustomFieldBehavior(field.fieldType).flatMap { behavior =>
        behavior.validate(field.fieldName, field.constraints, value, messages)
      }
    }
  }

  def apply(fieldType: String): Option[CustomFieldBehavior] = {
    fieldType match {
      case "long"   => Some(LongFieldBehavior)
      case "double" => Some(DoubleFieldBehavior)
      case "string" => Some(StringFieldBehavior)
      case "date"   => Some(DateFieldBehavior)
      case "enum"   => Some(EnumFieldBehavior)
      case _        => None
    }
  }

  case object LongFieldBehavior extends TextFieldBehavior {
    override def validate(
      name: String,
      constraints: Option[String],
      value: String,
      messages: Messages
    ): Option[String] = {
      try {
        value.toLong
        None
      } catch {
        case _: NumberFormatException => Some(messages("error.number").format(name))
      }
    }
  }
  case object DoubleFieldBehavior extends TextFieldBehavior {
    override def validate(
      name: String,
      constraints: Option[String],
      value: String,
      messages: Messages
    ): Option[String] = {
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

    override def validate(
      name: String,
      constraints: Option[String],
      value: String,
      messages: Messages
    ): Option[String] = {
      try {
        new java.text.SimpleDateFormat(pattern).parse(value)
        None
      } catch {
        case _: java.text.ParseException =>
          Some(messages("error.datePattern").format(name, pattern))
      }
    }
  }

  case object EnumFieldBehavior extends CustomFieldBehavior {
    override def createHtml(repository: RepositoryInfo, fieldId: Int, fieldName: String, constraints: Option[String])(
      implicit context: Context
    ): String = {
      createPulldownHtml(repository, fieldId, fieldName, constraints, None, None)
    }

    override def fieldHtml(
      repository: RepositoryInfo,
      issueId: Int,
      fieldId: Int,
      fieldName: String,
      constraints: Option[String],
      value: String,
      editable: Boolean
    )(implicit context: Context): String = {
      if (!editable) {
        val sb = new StringBuilder
        sb.append("""</div>""")
        sb.append("""<div>""")
        if (value == "") {
          sb.append(s"""<span id="label-custom-field-$fieldId"><span class="muted small">No ${StringUtil.escapeHtml(
            fieldName
          )}</span></span>""")
        } else {
          sb.append(s"""<span id="label-custom-field-$fieldId"><span class="muted small">${StringUtil
            .escapeHtml(value)}</span></span>""")
        }
        sb.toString()
      } else {
        createPulldownHtml(repository, fieldId, fieldName, constraints, Some(issueId), Some(value))
      }
    }

    private def createPulldownHtml(
      repository: RepositoryInfo,
      fieldId: Int,
      fieldName: String,
      constraints: Option[String],
      issueId: Option[Int],
      value: Option[String]
    )(implicit context: Context): String = {
      val sb = new StringBuilder
      sb.append("""<div class="pull-right">""")
      sb.append(
        gitbucket.core.helper.html
          .dropdown("Edit", right = true, filter = (fieldName, s"Filter $fieldName")) {
            val options = new StringBuilder()
            options.append(
              s"""<li><a href="javascript:void(0);" class="custom-field-option-$fieldId" data-value=""><i class="octicon octicon-x"></i> Clear ${StringUtil
                .escapeHtml(fieldName)}</a></li>"""
            )
            constraints.foreach {
              x =>
                x.split(",").map(_.trim).foreach {
                  item =>
                    options.append(s"""<li>
                 |  <a href="javascript:void(0);" class="custom-field-option-$fieldId" data-value="${StringUtil
                                        .escapeHtml(item)}">
                 |    ${gitbucket.core.helper.html.checkicon(value.contains(item))}
                 |    ${StringUtil.escapeHtml(item)}
                 |  </a>
                 |</li>
                 |""".stripMargin)
                }
            }
            Html(options.toString())
          }
          .toString()
      )
      sb.append("""</div>""")
      sb.append("""</div>""")
      sb.append("""<div>""")
      value match {
        case None =>
          sb.append(s"""<span id="label-custom-field-$fieldId"><span class="muted small">No ${StringUtil.escapeHtml(
            fieldName
          )}</span></span>""")
        case Some(value) =>
          sb.append(s"""<span id="label-custom-field-$fieldId"><span class="muted small">${StringUtil
            .escapeHtml(value)}</span></span>""")
      }
      if (value.isEmpty || issueId.isEmpty) {
        sb.append(s"""<input type="hidden" id="custom-field-$fieldId" name="custom-field-$fieldId" value=""/>""")
        sb.append(s"""<script>
             |$$('a.custom-field-option-$fieldId').click(function(){
             |  const value = $$(this).data('value');
             |  $$('a.custom-field-option-$fieldId i.octicon-check').removeClass('octicon-check');
             |  $$('#custom-field-$fieldId').val(value);
             |  if (value == '') {
             |    $$('#label-custom-field-$fieldId').html($$('<span class="muted small">').text('No ${StringUtil
                       .escapeHtml(fieldName)}'));
             |  } else {
             |    $$('#label-custom-field-$fieldId').html($$('<span class="muted small">').text(value));
             |    $$('a.custom-field-option-$fieldId[data-value=' + value + '] i').addClass('octicon-check');
             |  }
             |});
             |</script>""".stripMargin)
      } else {
        sb.append(s"""<script>
             |$$('a.custom-field-option-$fieldId').click(function(){
             |  const value = $$(this).data('value');
             |  $$.post('${helpers.url(repository)}/issues/${issueId.get}/customfield/$fieldId',
             |    { value: value },
             |    function(data){
             |      $$('a.custom-field-option-$fieldId i.octicon-check').removeClass('octicon-check');
             |      if (value == '') {
             |        $$('#label-custom-field-$fieldId').html($$('<span class="muted small">').text('No ${StringUtil
                       .escapeHtml(fieldName)}'));
             |      } else {
             |        $$('#label-custom-field-$fieldId').html($$('<span class="muted small">').text(value));
             |        $$('a.custom-field-option-$fieldId[data-value=' + value + '] i').addClass('octicon-check');
             |      }
             |    }
             |  );
             |});
             |</script>
             |""".stripMargin)
      }
      sb.toString()
    }

    override def validate(
      name: String,
      constraints: Option[String],
      value: String,
      messages: Messages
    ): Option[String] = None
  }

  trait TextFieldBehavior extends CustomFieldBehavior {
    protected val fieldType = "text"

    override def createHtml(repository: RepositoryInfo, fieldId: Int, fieldName: String, constraints: Option[String])(
      implicit context: Context
    ): String = {
      val sb = new StringBuilder
      sb.append(
        s"""<input type="$fieldType" class="form-control input-sm" id="custom-field-$fieldId" name="custom-field-$fieldId" data-field-id="$fieldId" style="width: 120px;"/>"""
      )
      sb.append(s"""<script>
                   |$$('#custom-field-$fieldId').focusout(function(){
                   |  const $$this = $$(this);
                   |  $$.post('${helpers.url(repository)}/issues/customfield_validation/$fieldId',
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

    override def fieldHtml(
      repository: RepositoryInfo,
      issueId: Int,
      fieldId: Int,
      fieldName: String,
      constraints: Option[String],
      value: String,
      editable: Boolean
    )(
      implicit context: Context
    ): String = {
      val sb = new StringBuilder
      if (value.nonEmpty) {
        sb.append(
          s"""<span id="custom-field-$fieldId-label" class="custom-field-label">${StringUtil
            .escapeHtml(value)}</span>"""
        )
      } else {
        if (editable) {
          sb.append(
            s"""<span id="custom-field-$fieldId-label" class="custom-field-label"><i class="octicon octicon-pencil" style="cursor: pointer;"></i></span>"""
          )
        } else {
          sb.append(
            s"""<span id="custom-field-$fieldId-label" class="custom-field-label">N/A</span>"""
          )
        }
      }
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
            |  $$.post('${helpers.url(repository)}/issues/customfield_validation/$fieldId',
            |    { value: $$this.val() },
            |    function(data){
            |      if (data != '') {
            |        $$('#custom-field-$fieldId-error').text(data);
            |      } else {
            |        $$('#custom-field-$fieldId-error').text('');
            |        $$.post('${helpers.url(repository)}/issues/$issueId/customfield/$fieldId',
            |          { value: $$this.val() },
            |          function(data){
            |            $$this.hide();
            |            if (data == '') {
            |              $$this.prev().html('<i class="octicon octicon-pencil" style="cursor: pointer;">').show();
            |            } else {
            |              $$this.prev().text(data).show();
            |            }
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

    override def validate(
      name: String,
      constraints: Option[String],
      value: String,
      messages: Messages
    ): Option[String] = None
  }
}
