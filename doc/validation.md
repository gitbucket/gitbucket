Mapping and Validation
========
GitBucket uses [scalatra-forms](https://github.com/takezoe/scalatra-forms) to validate request parameters and map them to the scala object. This is inspired by Play2 form mapping / validation.

At first, define the mapping as following:

```scala
import jp.sf.amateras.scalatra.forms._

case class RegisterForm(name: String, description: String)

val form = mapping(
  "name"        -> text(required, maxlength(40)),
  "description" -> text()
)(RegisterForm.apply)
```

The servlet have to mixed in ```jp.sf.amateras.scalatra.forms.ClientSideValidationFormSupport``` to validate request parameters and take mapped object. It validates request parameters before action. If any errors are detected, it throws an exception.

```scala
class RegisterServlet extends ScalatraServlet with ClientSideValidationFormSupport {
  post("/register", form) { form: RegisterForm =>
    ...
  }
}
```

In the view template, you can add client-side validation by adding ```validate="true"``` to your form. Error messages are set to ```span#error-<fieldname>```.

```html
<form method="POST" action="/register" validate="true">
  Name: <input type="name" type="text">
  <span class="error" id="error-name"></span>
  <br/>
  Description: <input type="description" type="text">
  <span class="error" id="error-description"></span>
  <br/>
  <input type="submit" value="Register"/>
</form>
```

Client-side validation calls ```<form-action>/validate``` to validate form contents. It returns a validation result as JSON. In this case, form action is ```/register```, so ```/register/validate``` is called before submitting a form. ```ClientSideValidationFormSupport``` adds this JSON API automatically.

For Ajax request, you have to use '''ajaxGet''' or '''ajaxPost''' to define action. It almost same as '''get''' or '''post'''. You can implement actions which handle Ajax request as same as normal actions.
Small difference is they return validation errors as JSON.

```scala
ajaxPost("/register", form){ form =>
  ...
}
```

You can call these actions using jQuery as below:

```javascript
$('#register').click(function(e){
  $.ajax($(this).attr('action'), {
    type: 'POST',
    data: {
      name: $('#name').val(),
      mail: $('#mail').val()
    }
  })
  .done(function(data){
    $('#result').text('Registered!');
  })
  .fail(function(data, status){
    displayErrors($.parseJSON(data.responseText));
  });
});
```
