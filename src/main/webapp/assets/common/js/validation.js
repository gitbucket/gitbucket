$(function(){
  $(document).on('submit', 'form[validate=true]', validate);
  $(document).on('click', 'input[formaction]', function(e){
    var form = $(e.target).parents('form');
    $(form).attr('action', $(e.target).attr('formaction'));
  });
});

function validate(e){
  var form = $(e.target);
  $(form).find('[type=submit]').attr('disabled', 'disabled');

  if(form.data('validated') === true){
    return true;
  }

  $.post(form.attr('action') + '/validate', $(e.target).serialize(), function(data){
    $(form).find('[type=submit]').removeAttr('disabled');
    // clear all error messages
    $('.error').text('');

    if($.isEmptyObject(data)){
      form.data('validated', true);
      form.submit();
      form.data('validated', false);
    } else {
      form.data('validated', false);
      displayErrors(data, form);
    }
  }, 'json').fail(function(xhr){
    if(xhr.status === 403){
      // refused, e.g. no valid CSRF token after the session ended: send the form, the server's answer says why
      form.data('validated', true);
      form.submit();
      form.data('validated', false);
    } else {
      // e.g. a network or server error: stay on the page, so the input isn't lost
      $(form).find('[type=submit]').removeAttr('disabled');
    }
  });
  return false;
}

function displayErrors(data){
  $.each(data, function(key, value){
    $('#error-' + key.split(".").join("_")).text(value);
  });
}
