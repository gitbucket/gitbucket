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

  // anchor icon for markdown
  $('.markdown-head').mouseenter(function(e){
    $(e.target).children('a.markdown-anchor-link').show();
  });
  $('.markdown-head').mouseleave(function(e){
    var anchorLink = $(e.target).children('a.markdown-anchor-link');
    if(anchorLink.data('active') != true){
      anchorLink.hide();
    }
  });

  $('a.markdown-anchor-link').mouseenter(function(e){
    $(e.target).data('active', true);
  });

  $('a.markdown-anchor-link').mouseleave(function(e){
    $(e.target).data('active', false);
    $(e.target).hide();
  });
  $(document).ready(function(){
    $('[data-url]').each(function(i,e) {
      var elem=$(e);
      $.ajax({
        url: elem.attr('data-url'),
        success: function(html){
          elem.replaceWith(html);
        }
      });
    });
  });

  // syntax highlighting by google-code-prettify
  prettyPrint();
});
