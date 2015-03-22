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
 * @param ignoreSpace {Number} 0: include, 1: ignore
 */
function diffUsingJS(oldTextId, newTextId, outputId, viewType, ignoreSpace) {
  var old = $('#'+oldTextId), head = $('#'+newTextId);
  var render = new JsDiffRender({
    oldText: old.val(),
    oldTextName: old.data('file-name'),
    newText: head.val(),
    newTextName: head.data('file-name'),
    ignoreSpace: ignoreSpace,
    contextSize: 4
  });
  var diff = render[viewType==1 ? "unified" : "split"]();
  diff.appendTo($('#'+outputId).html(""));
}



function jqSelectorEscape(val) {
    return val.replace(/[!"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~]/g, '\\$&');
}

function JsDiffRender(params){
  var baseTextLines = (params.oldText==="")?[]:params.oldText.split(/\r\n|\r|\n/);
  var headTextLines = (params.newText==="")?[]:params.newText.split(/\r\n|\r|\n/);
  var sm, ctx;
  if(params.ignoreSpace){
    var ignoreSpace = function(a){ return a.replace(/\s+/,' ').replace(/^\s+|\s+$/,''); };
    sm = new difflib.SequenceMatcher(
      $.map(baseTextLines, ignoreSpace),
      $.map(headTextLines, ignoreSpace));
    ctx = this.flatten(sm.get_opcodes(), headTextLines, baseTextLines, function(text){ return ignoreSpace(text) === ""; });
  }else{
    sm = new difflib.SequenceMatcher(baseTextLines, headTextLines);
    ctx = this.flatten(sm.get_opcodes(), headTextLines, baseTextLines, function(){ return false; });
  }
  var oplines = this.fold(ctx, params.contextSize);

  function prettyDom(text, fileName){
    var dom = null;
    return function(ln){
      if(dom===null){
        dom = prettyPrintOne(
          text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;').replace(/>/g,'&gt;'),
          (/\.([^.]*)$/.exec(fileName)||[])[1],
          true);
      }
      return (new RegExp('<li id="L'+ln+'"[^>]*>(.*?)</li>').exec(dom) || [])[1];
    };
  }
  return this.renders(oplines, prettyDom(params.oldText, params.oldTextName), prettyDom(params.newText, params.newTextName));
}
$.extend(JsDiffRender.prototype,{
  renders: function(oplines, baseTextDom, headTextDom){
    return {
      split:function(){
        var table = $('<table class="diff">');
        table.attr({add:oplines.add, del:oplines.del});
        var tbody = $('<tbody>').appendTo(table);
        for(var i=0;i<oplines.length;i++){
          var o = oplines[i];
          switch(o.change){
          case 'skip':
            $('<tr>').html('<th class="skip"></th><td colspan="3" class="skip">...</td>').appendTo(tbody);
            break;
          case 'delete':
          case 'insert':
          case 'equal':
            $('<tr>').append(
              lineNum('old',o.base, o.change),
              $('<td class="body">').html(o.base ? baseTextDom(o.base): "").addClass(o.change),
              lineNum('old',o.head, o.change),
              $('<td class="body">').html(o.head ? headTextDom(o.head): "").addClass(o.change)
              ).appendTo(tbody);
            break;
          case 'replace':
            var ld = lineDiff(baseTextDom(o.base), headTextDom(o.head));
            $('<tr>').append(
              lineNum('old',o.base, 'delete'),
              $('<td class="body">').append(ld.base).addClass('delete'),
              lineNum('old',o.head, 'insert'),
              $('<td class="body">').append(ld.head).addClass('insert')
              ).appendTo(tbody);
            break;
          }
        }
        return table;
      },
      unified:function(){
        var table = $('<table class="diff inlinediff">');
        table.attr({add:oplines.add, del:oplines.del});
        var tbody = $('<tbody>').appendTo(table);
        for(var i=0;i<oplines.length;i++){
          var o = oplines[i];
          switch(o.change){
          case 'skip':
            tbody.append($('<tr>').html('<th colspan="2" class="skip"></th><td class="skip"></td>'));
            break;
          case 'delete':
          case 'insert':
          case 'equal':
            tbody.append($('<tr>').append(
              lineNum('old',o.base, o.change),
              lineNum('new',o.head, o.change),
              $('<td class="body">').addClass(o.change).html(o.head ? headTextDom(o.head) : baseTextDom(o.base))));
            break;
          case 'replace':
            var deletes = [];
            while(oplines[i] && oplines[i].change == 'replace'){
              if(oplines[i].base && oplines[i].head){
                var ld = lineDiff(baseTextDom(oplines[i].base), headTextDom(oplines[i].head));
                tbody.append($('<tr>').append(lineNum('old', oplines[i].base, 'delete'),'<th class="delete">',$('<td class="body delete">').append(ld.base)));
                deletes.push($('<tr>').append('<th class="insert">',lineNum('new',oplines[i].head, 'insert'),$('<td class="body insert">').append(ld.head)));
              }else if(oplines[i].base){
                tbody.append($('<tr>').append(lineNum('old', oplines[i].base, 'delete'),'<th class="delete">',$('<td class="body delete">').html(baseTextDom(oplines[i].base))));
              }else if(oplines[i].head){
                deletes.push($('<tr>').append('<th class="insert">',lineNum('new',oplines[i].head, 'insert'),$('<td class="body insert">').html(headTextDom(oplines[i].head))));
              }
              i++;
            }
            tbody.append(deletes);
            i--;
            break;
          }
        }
        return table;
      }
    };
    function lineNum(type, num, klass){
      var cell = $('<th class="line-num">').addClass(type+'line').addClass(klass);
      if(num){
        cell.attr('line-number',num);
      }
      return cell;
    }
    function lineDiff(b,n){
      var bc = $('<diff>').html(b).children();
      var nc = $('<diff>').html(n).children();
      var textE = function(){ return $(this).text(); };
      var sm = new difflib.SequenceMatcher(bc.map(textE), nc.map(textE));
      var op = sm.get_opcodes();
      if(op.length==1 || sm.ratio()<0.5){
        return {base:bc,head:nc};
      }
      var ret = { base : [], head: []};
      for(var i=0;i<op.length;i++){
        var o = op[i];
        switch(o[0]){
        case 'equal':
          ret.base=ret.base.concat(bc.slice(o[1],o[2]));
          ret.head=ret.head.concat(nc.slice(o[3],o[4]));
          break;
        case 'delete':
        case 'insert':
        case 'replace':
          if(o[2]!=o[1]){
            ret.base.push($('<del>').append(bc.slice(o[1],o[2])));
          }
          if(o[4]!=o[3]){
            ret.head.push($('<ins>').append(nc.slice(o[3],o[4])));
          }
          break;
        }
      }
      return ret;
    }
  },
  flatten: function(opcodes, headTextLines, baseTextLines, isIgnoreLine){
    var ret = [], add=0, del=0;
    for (var idx = 0; idx < opcodes.length; idx++) {
      var code = opcodes[idx];
      var change = code[0];
      var b = code[1];
      var n = code[3];
      var rowcnt = Math.max(code[2] - b, code[4] - n);
      for (var i = 0; i < rowcnt; i++) {
        switch(change){
        case 'insert':
          add++;
          ret.push({
            change:(isIgnoreLine(headTextLines[n]) ? 'equal' : change),
            head: ++n
          });
          break;
        case 'delete':
          del++;
          ret.push({
            change: (isIgnoreLine(baseTextLines[b]) ? 'equal' : change),
            base: ++b
          });
          break;
        case 'replace':
          add++;
          del++;
          var r = {change: change};
          if(n<code[4]){
            r.head = ++n;
          }
          if(b<code[2]){
            r.base = ++b;
          }
          ret.push(r);
          break;
        default:
          ret.push({
            change:change,
            head: ++n,
            base: ++b
          });
        }
      }
    }
    ret.add=add;
    ret.del=del;
    return ret;
  },
  fold: function(oplines, contextSize){
    var ret = [], skips=[], bskip = contextSize;
    for(var i=0;i<oplines.length;i++){
      var o = oplines[i];
      if(o.change=='equal'){
        if(bskip < contextSize){
          bskip ++;
          ret.push(o);
        }else{
          skips.push(o);
        }
      }else{
        if(skips.length > contextSize){
          ret.push({
            change:'skip',
            start:skips[0],
            end:skips[skips.length-contextSize]
          });
        }
        ret = ret.concat(skips.splice(- contextSize));
        ret.push(o);
        skips = [];
        bskip = 0;
      }
    }
    if(skips.length > contextSize){
      ret.push({
        change:'skip',
        start:skips[0],
        end:skips[skips.length-contextSize]
      });
    }
    ret.add = oplines.add;
    ret.del = oplines.del;
    return ret;
  }
});
