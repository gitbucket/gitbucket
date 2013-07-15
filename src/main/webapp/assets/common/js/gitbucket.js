$(function(){
  $.ajaxSetup({ cache: false });

  $('#repository-url').click(function(){
    this.select(0, this.value.length); 
  });

  $('img[data-toggle=tooltip]').tooltip();
  $('a[data-toggle=tooltip]').tooltip();

  // copy to clipboard
  (function() {
    // Find ZeroClipboard.swf file URI from ZeroClipboard JavaScript file path.
    // NOTE(tanacasino) I think this way is wrong... but i don't know correct way.
    var moviePath = (function() {
      var zclipjs = "ZeroClipboard.min.js";
      var scripts = document.getElementsByTagName("script");
      var i = scripts.length;
      while(i--) {
        var match = scripts[i].src.match(zclipjs + "$");
        if(match) {
          return match.input.substr(0, match.input.length - 6) + 'swf';
        }
      }
    })();
    var clip = new ZeroClipboard($("#repository-url-copy"), {
      moviePath: moviePath
    });
    var title = $('#repository-url-copy').attr('title');
    clip.on('complete', function(client, args) {
      $(clip.htmlBridge).attr('title', 'copied!').tooltip('fixTitle').tooltip('show');
      $(clip.htmlBridge).attr('title', title).tooltip('fixTitle');
    });
    $(clip.htmlBridge).tooltip({
      title: title,
      placement: $('#repository-url-copy').attr('data-placement')
    });
  })();

  prettyPrint();
});
