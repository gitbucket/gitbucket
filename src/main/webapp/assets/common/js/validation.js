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
  }, 'json');
  return false;
}

function displayErrors(data){
  $.each(data, function(key, value){
    $('#error-' + key.split(".").join("_")).text(value);
  });
}
