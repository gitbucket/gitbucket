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

  // activate hotkey
  $('a[data-hotkey]').each(function(){
    var target = this;
    $(document).bind('keydown', $(target).data('hotkey'), function(){ target.click(); });
  });

  // anchor icon for markdown
  $('.markdown-head').on('mouseenter', function(e){
    $(this).find('span.octicon').css('visibility', 'visible');
  });
  $('.markdown-head').on('mouseleave', function(e){
    $(this).find('span.octicon').css('visibility', 'hidden');
  });

  // syntax highlighting by google-code-prettify
  prettyPrint();

  // Suppress transition animation on load
  $("body").removeClass("page-load");
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
 * @param fileHash {SString} hash used for links to line numbers
 */
function diffUsingJS(oldTextId, newTextId, outputId, viewType, ignoreSpace, fileHash) {
  const old = $('#' + oldTextId), head = $('#' + newTextId);
  let oldTextValue, headTextValue;
  old.is("textarea") ? (oldTextValue = old.data('val')) : (oldTextValue = old.attr('data-val'));
  head.is("textarea") ? (headTextValue = head.data('val')) : (headTextValue = head.attr('data-val'));
  const render = new JsDiffRender({
    oldText    : oldTextValue,
    oldTextName: old.data('file-name'),
    newText    : headTextValue,
    newTextName: head.data('file-name'),
    ignoreSpace: ignoreSpace,
    contextSize: 4,
    fileHash   : fileHash
  });
  const diff = render[viewType == 1 ? "unified" : "split"]();
  if (viewType == 1) {
    diff.find('tr:last').after($('<tr><td></td><td></td><td></td></tr>'));
  } else {
    diff.find('tr:last').after($('<tr><td></td><td></td><td></td><td></td></tr>'));
  }
  diff.appendTo($('#' + outputId).html(""));
}



function jqSelectorEscape(val) {
    return val.replace(/[!"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~]/g, '\\$&');
}

function JsDiffRender(params) {
  const baseTextLines = (params.oldText==="")?[]:params.oldText.split(/\r\n|\r|\n/);
  const headTextLines = (params.newText==="")?[]:params.newText.split(/\r\n|\r|\n/);
  let sm, ctx;
  if (params.ignoreSpace) {
    const ignoreSpace = function(a){ return a.replace(/\s+/g,''); };
    sm = new difflib.SequenceMatcher(
      $.map(baseTextLines, ignoreSpace),
      $.map(headTextLines, ignoreSpace));
    ctx = this.flatten(sm.get_opcodes(), headTextLines, baseTextLines, function(text){ return ignoreSpace(text) === ""; });
  } else {
    sm = new difflib.SequenceMatcher(baseTextLines, headTextLines);
    ctx = this.flatten(sm.get_opcodes(), headTextLines, baseTextLines, function(){ return false; });
  }
  const oplines = this.fold(ctx, params.contextSize);

  function prettyDom(text, fileName){
    let dom = null;
    return function(ln) {
      if(dom === null) {
        const html = prettyPrintOne(
          text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;').replace(/>/g,'&gt;').replace(/^\n/, '\n\n'),
          (/\.([^.]*)$/.exec(fileName)||[])[1],
          true);
        const re = /<li[^>]*id="?L([0-9]+)"?[^>]*>(.*?)<\/li>/gi;
        let h;
        dom = [];
        while (h = re.exec(html)) {
          dom[h[1]] = h[2];
        }
      }
      return dom[ln];
    };
  }
  return this.renders(oplines, prettyDom(params.oldText, params.oldTextName), prettyDom(params.newText, params.newTextName), params.fileHash);
}
$.extend(JsDiffRender.prototype,{
  renders: function(oplines, baseTextDom, headTextDom, fileHash){
    return {
      split: function(){
        const table = $('<table class="diff">');
        table.attr({ add: oplines.add, del: oplines.del });
        const tbody = $('<tbody>').appendTo(table);
        for (let i = 0; i < oplines.length; i++) {
          const o = oplines[i];
          switch (o.change) {
            case 'skip':
              $('<tr>').html('<th class="skip"></th><td colspan="3" class="skip">...</td>').appendTo(tbody);
              break;
            case 'delete':
            case 'insert':
            case 'equal':
              $('<tr>').append(
                lineNum('old', o.base, o.change, fileHash),
                $('<td class="body">').html(o.base ? baseTextDom(o.base): "").addClass(o.change),
                lineNum('new', o.head, o.change, fileHash),
                $('<td class="body">').html(o.head ? headTextDom(o.head): "").addClass(o.change)
              ).appendTo(tbody);
              break;
            case 'replace':
              const ld = lineDiff(baseTextDom(o.base), headTextDom(o.head));
              $('<tr>').append(
                lineNum('old', o.base, 'delete', fileHash),
                $('<td class="body">').append(ld.base).addClass('delete'),
                lineNum('new', o.head, 'insert', fileHash),
                $('<td class="body">').append(ld.head).addClass('insert')
              ).appendTo(tbody);
              break;
          }
        }
        return table;
      },
      unified: function(){
        const table = $('<table class="diff inlinediff">');
        table.attr({ add: oplines.add, del: oplines.del });
        const tbody = $('<tbody>').appendTo(table);
        for (let i = 0; i < oplines.length; i++) {
          const o = oplines[i];
          switch (o.change) {
            case 'skip':
              tbody.append($('<tr>').html('<th colspan="2" class="skip"></th><td class="skip"></td>'));
              break;
            case 'delete':
            case 'insert':
            case 'equal':
              tbody.append($('<tr>').append(
                lineNum('old', o.base, o.change, fileHash),
                lineNum('new', o.head, o.change, fileHash),
                $('<td class="body">').addClass(o.change).html(o.head ? headTextDom(o.head) : baseTextDom(o.base))));
              break;
            case 'replace':
              const deletes = [];
              while (oplines[i] && oplines[i].change == 'replace') {
                if (oplines[i].base && oplines[i].head) {
                  const ld = lineDiff(baseTextDom(oplines[i].base), headTextDom(oplines[i].head));
                  tbody.append($('<tr>').append(lineNum('old', oplines[i].base, 'delete', fileHash), '<th class="delete">', $('<td class="body delete">').append(ld.base)));
                  deletes.push($('<tr>').append('<th class="insert">',lineNum('new', oplines[i].head, 'insert', fileHash),$('<td class="body insert">').append(ld.head)));
                } else if(oplines[i].base) {
                  tbody.append($('<tr>').append(lineNum('old', oplines[i].base, 'delete', fileHash), '<th class="delete">', $('<td class="body delete">').html(baseTextDom(oplines[i].base))));
                } else if(oplines[i].head) {
                  deletes.push($('<tr>').append('<th class="insert">',lineNum('new', oplines[i].head, 'insert', fileHash), $('<td class="body insert">').html(headTextDom(oplines[i].head))));
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
    function lineNum(type, num, klass, hash) {
      const cell = $('<th class="line-num">').addClass(type + 'line').addClass(klass);
      if (num) {
        cell.attr('line-number', num);
        cell.attr('id', hash + '-' + (type == 'old' ? 'L' : 'R') + num);
      }
      return cell;
    }
    function lineDiff(b, n) {
      const bc = $('<diff>').html(b).children();
      const nc = $('<diff>').html(n).children();
      const textE = function(){ return $(this).text(); };
      const sm = new difflib.SequenceMatcher(bc.map(textE), nc.map(textE));
      const op = sm.get_opcodes();
      if (op.length == 1 || sm.ratio() < 0.5) {
        return { base:bc, head:nc };
      }
      const ret = { base : [], head: []};
      for (let i = 0; i < op.length; i++) {
        const o = op[i];
        switch (o[0]) {
          case 'equal':
            ret.base = ret.base.concat(bc.slice(o[1], o[2]));
            ret.head = ret.head.concat(nc.slice(o[3], o[4]));
            break;
          case 'delete':
          case 'insert':
          case 'replace':
            if(o[2] != o[1]){
              ret.base.push($('<del>').append(bc.slice(o[1], o[2])));
            }
            if(o[4] != o[3]){
              ret.head.push($('<ins>').append(nc.slice(o[3], o[4])));
            }
            break;
        }
      }
      return ret;
    }
  },
  flatten: function(opcodes, headTextLines, baseTextLines, isIgnoreLine){
    let ret = [], add = 0, del = 0;
    for (let idx = 0; idx < opcodes.length; idx++) {
      const code = opcodes[idx];
      const change = code[0];
      let b = code[1];
      let n = code[3];
      const rowcnt = Math.max(code[2] - b, code[4] - n);
      for (let i = 0; i < rowcnt; i++) {
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
            const r = { change: change };
            if (n<code[4]) {
              r.head = ++n;
            }
            if (b<code[2]) {
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
    ret.add = add;
    ret.del = del;
    return ret;
  },
  fold: function(oplines, contextSize){
    let ret = [], skips=[], bskip = contextSize;
    for (let i = 0; i < oplines.length; i++) {
      const o = oplines[i];
      if (o.change=='equal') {
        if (bskip < contextSize) {
          bskip ++;
          ret.push(o);
        } else {
          skips.push(o);
        }
      } else {
        if (skips.length > contextSize) {
          ret.push({
            change: 'skip',
            start: skips[0],
            end: skips[skips.length-contextSize]
          });
        }
        ret = ret.concat(skips.splice(- contextSize));
        ret.push(o);
        skips = [];
        bskip = 0;
      }
    }
    if (skips.length > contextSize) {
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

/**
 * scroll target into view ( on bottom edge, or on top edge)
 */
function scrollIntoView(target){
  target = $(target);
  const $window = $(window);
  const docViewTop = $window.scrollTop();
  const docViewBottom = docViewTop + $window.height();

  const elemTop = target.offset().top;
  const elemBottom = elemTop + target.height();

  if(elemBottom > docViewBottom){
    $('html, body').scrollTop(elemBottom - $window.height());
  }else if(elemTop < docViewTop){
    $('html, body').scrollTop(elemTop);
  }
}

/**
* escape html
*/
function escapeHtml(text){
 return text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;').replace(/>/g,'&gt;');
}

/**
 * calculate string ranking for path.
 * Original ported from:
 * http://joshaven.com/string_score
 * https://github.com/joshaven/string_score
 *
 * Copyright (C) 2009-2011 Joshaven Potter <yourtech@@gmail.com>
 * Special thanks to all of the contributors listed here https://github.com/joshaven/string_score
 * MIT license: http://www.opensource.org/licenses/mit-license.php
 */
function string_score(string, word) {
  'use strict';
  var zero = {score:0,matchingPositions:[]};

  // If the string is equal to the word, perfect match.
  if (string === word || word === "") { return {score:1, matchingPositions:[]}; }

  var lString = string.toUpperCase(),
      strLength = string.length,
      lWord = word.toUpperCase(),
      wordLength = word.length;

  return   calc(zero,        0,    0,            0, 0,                []);
  function calc(score, startAt, skip, runningScore, i, matchingPositions){
    if( i < wordLength) {
      var charScore = 0;

      // Find next first case-insensitive match of a character.
      var idxOf = lString.indexOf(lWord[i], skip);

      if (-1 === idxOf) { return score; }
      score = calc(score, startAt, idxOf+1, runningScore, i, matchingPositions);
      if (startAt === idxOf) {
        // Consecutive letter & start-of-string Bonus
        charScore = 0.8;
      } else {
        charScore = 0.1;

        // Acronym Bonus
        // Weighing Logic: Typing the first character of an acronym is as if you
        // preceded it with two perfect character matches.
        if (/^[^A-Za-z0-9]/.test(string[idxOf - 1])){
          charScore += 0.7;
        }else if(string[idxOf]==lWord[i]) {
          // Upper case bonus
          charScore += 0.2;
          // Camel case bonus
          if(/^[a-z]/.test(string[idxOf - 1])){
            charScore += 0.5;
          }
        }
      }

      // Same case bonus.
      if (string[idxOf] === word[i]) { charScore += 0.1; }

      // next round
      return calc(score, idxOf + 1, idxOf + 1, runningScore + charScore, i+1, matchingPositions.concat(idxOf));
    }else{
      // skip non match folder
      var effectiveLength = strLength;
      if(matchingPositions.length){
        var lastSlash = string.lastIndexOf('/',matchingPositions[0]);
        if(lastSlash!==-1){
          effectiveLength = strLength-lastSlash;
        }
      }
      // Reduce penalty for longer strings.
      var finalScore = 0.5 * (runningScore / effectiveLength + runningScore / wordLength);

      if ((lWord[0] === lString[0]) && (finalScore < 0.85)) {
        finalScore += 0.15;
      }
      if(score.score >= finalScore){
        return score;
      }
      return {score:finalScore, matchingPositions:matchingPositions};
    }
  }
}
/**
 * sort by string_score.
 * @param word    {String}        search word
 * @param strings {Array[String]} search targets
 * @param limit   {Integer}       result limit
 * @return {Array[{score:"float matching score", string:"string target string", matchingPositions:"Array[Integer] matching positions"}]}
 */
function string_score_sort(word, strings, limit){
  var ret = [], i=0, l = (word==="")?Math.min(strings.length, limit):strings.length;
  for(; i < l; i++){
    var score = string_score(strings[i],word);
    if(score.score){
      score.string = strings[i];
      ret.push(score);
    }
  }
  ret.sort(function(a,b){
    var s = b.score - a.score;
    if(s === 0){
      return a.string > b.string ? 1 : -1;
    }
    return s;
  });
  ret = ret.slice(0,limit);
  return ret;
}
/**
 * highlight by result.
 * @param score {string:"string target string", matchingPositions:"Array[Integer] matching positions"}
 * @param highlight tag ex: '<b>'
 * @return array of highlighted html elements.
 */
function string_score_highlight(result, tag){
  var str = result.string, msp=0;
  return hilight([], 0,  result.matchingPositions[msp]);
  function hilight(html, c, mpos){
    if(mpos === undefined){
      return html.concat(document.createTextNode(str.substr(c)));
    }else{
      return hilight(html.concat([
                     document.createTextNode(str.substring(c,mpos)),
                     $(tag).text(str[mpos])]),
                     mpos+1, result.matchingPositions[++msp]);
    }
  }
}

/****************************************************************************/
/* Diff */
/****************************************************************************/
// add naturalWidth and naturalHeight for ie 8
function setNatural(img) {
  if(typeof img.naturalWidth == 'undefined'){
    var tmp = new Image();
    tmp.src = img.src;
    img.naturalWidth = tmp.width;
    img.naturalHeight = tmp.height;
  }
}
/**
 * onload handler
 * @param img <img>
 */
function onLoadedDiffImages(img){
  setNatural(img);
  img = $(img);
  img.show();
  var tb = img.parents(".diff-image-render");
  // Find images. If the image has not loaded yet, value is undefined.
  var old = tb.find(".diff-old img.diff-image:visible")[0];
  var neo = tb.find(".diff-new img.diff-image:visible")[0];
  imageDiff.appendImageMeta(tb, old, neo);
  if(old && neo){
    imageDiff.createToolSelector(old, neo).appendTo(tb.parent());
  }
}
var imageDiff ={
  /** append image meta div after image nodes.
   * @param tb <div class="diff-image-2up">
   * @param old <img>||undefined
   * @param neo <img>||undefined
   */
  appendImageMeta:function(tb, old, neo){
    old = old || {};
    neo = neo || {};
    tb.find(".diff-meta").remove();
    // before loaded, image is not visible.
    tb.find("img.diff-image:visible").each(function(){
      var div = $('<p class="diff-meta"><b>W:</b><span class="w"></span> | <b>W:</b><span class="h"></span></p>');
      div.find('.w').text(this.naturalWidth+"px").toggleClass("diff", old.naturalWidth != neo.naturalWidth);
      div.find('.h').text(this.naturalHeight+"px").toggleClass("diff", old.naturalHeight != neo.naturalHeight);
      div.appendTo(this.parentNode);
    });
  },
  /** check this browser can use canvas tag.
   */
  hasCanvasSupport:function(){
    if(!this.hasCanvasSupport.hasOwnProperty('resultCache')){
      this.hasCanvasSupport.resultCache = (typeof $('<canvas>')[0].getContext)=='function';
    }
    return this.hasCanvasSupport.resultCache;
  },
  /** create toolbar
   * @param old <img>
   * @param neo <img>
   * @return jQuery(<ul class="image-diff-tools">)
   */
  createToolSelector:function(old, neo){
    var self = this;
    return $('<ul class="image-diff-tools">'+
      '<li data-mode="diff2up" class="active">2-up</li>'+
      '<li data-mode="swipe">Swipe</li>'+
      '<li data-mode="onion">Onion Skin</li>'+
      '<li data-mode="difference" class="need-canvas">Difference</li>'+
      '<li data-mode="blink">Blink</li>'+
      '</ul>')
      .toggleClass('no-canvas', !this.hasCanvasSupport())
      .on('click', 'li', function(e){
        var td = $(this).parents("td");
        $(e.delegateTarget).find('li').each(function(){ $(this).toggleClass('active',this == e.target); });
        var mode = $(e.target).data('mode');
        td.find(".diff-image-render").hide();
        // create div if not created yet
        if(td.find(".diff-image-render."+mode).show().length===0){
          self[mode](old, neo).insertBefore(e.delegateTarget).addClass("diff-image-render");
        }
        return false;
      });
  },
  /** (private) calc size from images and css (const)
   * @param old <img>
   * @param neo <img>
   */
  calcSizes:function(old, neo){
    var maxWidth = 869 - 20 - 20 - 4; // set by css
    var h = Math.min(Math.max(old.naturalHeight, neo.naturalHeight),maxWidth);
    var w = Math.min(Math.max(old.naturalWidth, neo.naturalWidth),maxWidth);
    var oldRate = Math.min(h/old.naturalHeight, w/old.naturalWidth);
    var neoRate = Math.min(h/neo.naturalHeight, w/neo.naturalWidth);
    var neoW = neo.naturalWidth*neoRate;
    var neoH = neo.naturalHeight*neoRate;
    var oldW = old.naturalWidth*oldRate;
    var oldH = old.naturalHeight*oldRate;
    var paddingLeft = (maxWidth/2)-Math.max(neoW,oldW)/2;
    return {
      height:Math.max(oldH, neoH),
      width:w,
      padding:paddingLeft,
      paddingTop:20, // set by css
      barWidth:200, // set by css
      old:{ rate:oldRate, width:oldW, height:oldH },
      neo:{ rate:neoRate, width:neoW, height:neoH }
    };
  },
  /** (private) create div
   * @param old <img>
   * @param neo <img>
   */
  stack:function(old, neo){
    var size = this.calcSizes(old, neo);
    var diffNew = $('<div class="diff-new">')
      .append($("<img>").attr('src',neo.src).css({width:size.neo.width, height:size.neo.height}));
    var diffOld = $('<div class="diff-old">')
      .append($("<img>").attr('src',old.src).css({width:size.old.width, height:size.old.height}));
    var handle =  $('<span class="diff-swipe-handle icon icon-resize-horizontal"></span>')
      .css({marginTop:size.height-5});
    var bar = $('<hr class="diff-silde-bar">').css({top:size.height+size.paddingTop});
    var div = $('<div class="diff-image-stack">')
      .css({height:size.height+size.paddingTop*2, paddingLeft:size.padding})
      .append(diffOld, diffNew, bar, handle);
    return {
      neo:diffNew,
      old:diffOld,
      size:size,
      handle:handle,
      bar:bar,
      div:div,
      /* add event listener 'on mousemove' */
      onMoveHandleOnBar:function(callback){
        div.on('mousemove',function(e){
          var x = Math.max(Math.min((e.pageX - bar.offset().left), size.barWidth), 0);
          handle.css({left:x});
          callback(x, e);
        });
      }
    };
  },
  /** create swipe box
   * @param old <img>
   * @param neo <img>
   * @return jQuery(<div class="diff-image-stack swipe">)
   */
  swipe:function(old, neo){
    var stack = this.stack(old, neo);
    function setX(x){
      stack.neo.css({width:x});
      stack.handle.css({left:x+stack.size.padding});
    }
    setX(stack.size.neo.width/2);
    stack.div.on('mousemove',function(e){
      setX(Math.max(Math.min(e.pageX - stack.neo.offset().left, stack.size.neo.width),0));
    });
    return stack.div.addClass('swipe');
  },
  /** create blink box
   * @param old <img>
   * @param neo <img>
   * @return jQuery(<div class="diff-image-stack blink">)
   */
  blink:function(old, neo){
    var stack = this.stack(old, neo);
    stack.onMoveHandleOnBar(function(x){
      stack.neo.toggle(Math.floor(x) % 2 === 0);
    });
    return stack.div.addClass('blink');
  },
  /** create onion skin box
   * @param old <img>
   * @param neo <img>
   * @return jQuery(<div class="diff-image-stack onion">)
   */
  onion:function(old, neo){
    var stack = this.stack(old, neo);
    stack.neo.css({opacity:0.5});
    stack.onMoveHandleOnBar(function(x){
      stack.neo.css({opacity:x/stack.size.barWidth});
    });
    return stack.div.addClass('onion');
  },
  /** create difference box
   * @param old <img>
   * @param neo <img>
   * @return jQuery(<div class="diff-image-stack difference">)
   */
  difference:function(old, neo){
    var size = this.calcSizes(old,neo);
    var canvas = $('<canvas>').attr({width:size.width, height:size.height})[0];
    var context = canvas.getContext('2d');

    context.clearRect(0, 0, size.width, size.height);
    context.drawImage(neo, 0, 0, size.neo.width, size.neo.height);
    var neoData = context.getImageData(0, 0, size.neo.width, size.neo.height).data;
    context.clearRect(0, 0, size.width, size.height);

    context.drawImage(old, 0, 0, size.old.width, size.old.height);
    var c = context.getImageData(0, 0, size.neo.width, size.neo.height);
    var cData = c.data;
    for (var i = cData.length -1; i>0; i --){
      cData[i] = (i%4===3) ? Math.max(cData[i], neoData[i]) : Math.abs(cData[i] - neoData[i]);
    }
    context.putImageData(c, 0, 0);

    return $('<div class="diff-image-stack difference">').append(canvas);
  }
};

/**
 * function for account extra mail address form control.
 */
function addExtraMailAddress() {
  var fieldset = $('#extraMailAddresses');
  var count = $('.extraMailAddress').length;
  var html =   '<input type="text" name="extraMailAddresses[' + count + ']" id="extraMailAddresses[' + count + ']" class="form-control extraMailAddress" aria-label="Additional mail address"/>'
  + '<span id="error-extraMailAddresses_' + count + '" class="error"></span>';
  fieldset.append(html);
}

/**
 * function for check account extra mail address form control.
 */
function checkExtraMailAddress(){
  if ($(this).val() != ""){
    var needAdd = true;
    $('.extraMailAddress').each(function(){
      if($(this).val() == ""){
        needAdd = false;
        return false;
      }
      return true;
    });
    if (needAdd){
      addExtraMailAddress();
    }
  }
  else {
    $(this).remove();
  }
}

/**
 * function for extracting markdown from comment area.
 * @param commentArea a comment area
 * @returns {*|jQuery}
 */
var extractMarkdown = function(commentArea){
  $('body').append('<div id="tmp"></div>');
  $('#tmp').html(commentArea);
  var markdown = $('#tmp textarea').val();
  $('#tmp').remove();
  return markdown;
};

/**
 * function for applying checkboxes status of task list.
 * @param commentArea a comment area
 * @param checkboxes checkboxes for task list
 * @returns {string} a markdown that applied checkbox status
 */
var applyTaskListCheckedStatus = function(commentArea, checkboxes) {
  var ss = [],
    markdown = extractMarkdown(commentArea),
    xs = markdown.split(/- \[[x| ]\]/g);
  for (var i=0; i<xs.length; i++) {
    ss.push(xs[i]);
    if (checkboxes.eq(i).prop('checked')) ss.push('- [x]');
    else ss.push('- [ ]');
  }
  ss.pop();
  return ss.join('');
};
