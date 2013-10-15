$(function(){
  // disable Ajax cache
  $.ajaxSetup({ cache: false });

  // repository url text field
  $('#repository-url').click(function(){
    this.select(0, this.value.length); 
  });

  // activate tooltip
  $('img[data-toggle=tooltip]').tooltip();
  $('a[data-toggle=tooltip]').tooltip();

  // syntax highlighting by google-code-prettify
  prettyPrint();
});
