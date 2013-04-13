$(function(){
	$.each($('form[validate=true]'), function(i, form){
		$(form).submit(validate);
	});
});

function validate(e){
	var form = $(e.target);
	
	if(form.data('validated') == true){
		return true;
	}
	
	// TODO use $.post() instead of $.getJSON
	$.getJSON(form.attr('action') + '/validate', $(e.target).serialize(), function(data){
		// clear all error messages
		$('.error-message').text('');
		
		if(data.valid){
			form.data('validated', true);
			form.submit();
		} else {
			$.each(data.errors, function(key, value){
				$('#error-' + key).text(value);
			});
		}
	});
	return false;
}
