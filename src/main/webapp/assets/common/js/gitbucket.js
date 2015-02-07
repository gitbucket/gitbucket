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
  $('li[data-toggle=tooltip]').tooltip();

  // anchor icon for markdown
  $('.markdown-head').mouseenter(function(e){
    $(e.target).children('a.markdown-anchor-link').show();
  });
  $('.markdown-head').mouseleave(function(e){
    var anchorLink = $(e.target).children('a.markdown-anchor-link');
    if(anchorLink.data('active') !== true){
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

  // syntax highlighting by google-code-prettify
  prettyPrint();
});

function displayErrors(data, elem){
  var i = 0;
  $.each(data, function(key, value){
    $('#error-' + key.split(".").join("_"), elem).text(value);
    if(i === 0){
      $('#' + key, elem).focus();
    }
    i++;
  });
}

(function($){
  $.fn.watch = function(callback){
    var timer = null;
    var prevValue = this.val();

    this.on('focus', function(e){
      window.clearInterval(timer);
      timer = window.setInterval(function(){
        var newValue = $(e.target).val();
        if(prevValue != newValue){
          callback();
        }
        prevValue = newValue;
      }, 10);
    });

    this.on('blur', function(){
      window.clearInterval(timer);
    });
  };
})(jQuery);

/**
 * Render diff using jsdifflib.
 *
 * @param oldTextId {String} element id of old text
 * @param newTextId {String} element id of new text
 * @param outputId {String} element id of output element
 * @param viewType {Number} 0: split, 1: unified
 */
function diffUsingJS(oldTextId, newTextId, outputId, viewType) {
  // get the baseText and newText values from the two textboxes, and split them into lines
  var oldText = document.getElementById(oldTextId).value;
  var oldLines = [];
  if(oldText !== ''){
    oldLines = difflib.stringAsLines(oldText);
  }

  var newText = document.getElementById(newTextId).value;
  var newLines = [];
  if(newText !== ''){
    newLines = difflib.stringAsLines(newText);
  }

  // create a SequenceMatcher instance that diffs the two sets of lines
  var sm = new difflib.SequenceMatcher(oldLines, newLines);

  // get the opcodes from the SequenceMatcher instance
  // opcodes is a list of 3-tuples describing what changes should be made to the base text
  // in order to yield the new text
  var opcodes = sm.get_opcodes();
  var diffoutputdiv = document.getElementById(outputId);
  while (diffoutputdiv.firstChild) diffoutputdiv.removeChild(diffoutputdiv.firstChild);

  // build the diff view and add it to the current DOM
  diffoutputdiv.appendChild(diffview.buildView({
    baseTextLines: oldLines,
    newTextLines: newLines,
    opcodes: opcodes,
    contextSize: 4,
    viewType: viewType
  }));
}

function jqSelectorEscape(val) {
    return val.replace(/[!"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~]/g, '\\$&');
}
