$(function(){
  $.each($('form[validate=true]'), function(i, form){
    $(form).submit(validate);
  });
  $.each($('input[formaction]'), function(i, input){
    $(input).click(function(){
      var form = $(input).parents('form');
      $(form).attr('action', $(input).attr('formaction'));
    });
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
      displayErrors(data);
    }
  }, 'json');
  return false;
}

function displayErrors(data){
  $.each(data, function(key, value){
    $('#error-' + key.split(".").join("_")).text(value);
  });
}
