define("ace/mode/basic_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var BasicHighlightRules = function () {
    var keywordMapper = this.createKeywordMapper({
        "keyword.control": "FOR|TO|NEXT|GOSUB|RETURN|IF|THEN|ELSE|GOTO|ON|WHILE|WEND|TRON|TROFF",
        "entity.name": "Auto|Call|Chain|Clear|Close|Common|Cont|Data|MERGE|ALL|Delete|DIM|EDIT|END|ERASE|ERROR|FIELD|"
            + "GET|INPUT|KILL|LET|LIST|LLIST|LOAD|LSET|RSET|MERGE|NEW|NULL|OPEN|OUT|POKE|PRINT|PUT|RANDOMIZE|READ|"
            + "RENUM|RESTORE|RESUME|RUN|SAVE|STOP|SWAP|WAIT|WIDTH",
        "keyword.operator": "Mod|And|Not|Or|Xor|Eqv|Imp",
        "support.function": "ABS|ASC|ATN|CDBL|CINT|COS|CSNG|CVI|CVS|CVD|EOF|EXP|FIX|FRE|INP|INSTR|INT|LEN|LOC|LOG|LPOS|"
            + "PEEK|POS|RND|SGN|SIN|SPC|SQR|TAB|TAN|USR|VAL|VARPTR"
    }, "identifier", true);
    this.$rules = {
        "start": [
            {
                token: "string",
                regex: /"(?:\\.|[^"\\])*"/
            },
            {
                token: "support.function",
                regex: /(HEX|CHR|INPUT|LEFT|MID|MKI|MKS|MKD|OCT|RIGHT|SPACE|STR|STRING)\$/
            }, {
                token: "entity.name",
                regex: /(?:DEF\s(?:SEG|USR|FN[a-zA-Z]+)|LINE\sINPUT|L?PRINT#?(?:\sUSING)?|MID\$|ON\sERROR\sGOTO|OPTION\sBASE|WRITE#?|DATE\$|INKEY\$|TIME\$)/
            }, {
                token: "variable",
                regex: /[a-zA-Z][a-zA-Z0-9_]{0,38}[$%!#]?(?=\s*=)/
            }, {
                token: "keyword.operator",
                regex: /\\|=|\^|\*|\/|\+|\-|<|>|-/
            }, {
                token: "paren.lparen",
                regex: /[([]/
            }, {
                token: "paren.rparen",
                regex: /[\)\]]/
            }, {
                token: "constant.numeric",
                regex: /[+-]?\d+(\.\d+)?([ED][+-]?\d+)?(?:[!#])?/
            }, {
                token: "constant.numeric", //hexal, octal
                regex: /&[HO]?[0-9A-F]+/
            }, {
                token: "comment",
                regex: /REM\s+.*$/
            }, {
                regex: "\\w+",
                token: keywordMapper
            }, {
                token: "punctiation",
                regex: /[,;]/
            }
        ]
    };
    this.normalizeRules();
};
oop.inherits(BasicHighlightRules, TextHighlightRules);
exports.BasicHighlightRules = BasicHighlightRules;

});

define("ace/mode/folding/basic",["require","exports","module","ace/lib/oop","ace/mode/folding/fold_mode","ace/range","ace/token_iterator"], function(require, exports, module){"use strict";
var oop = require("../../lib/oop");
var BaseFoldMode = require("./fold_mode").FoldMode;
var Range = require("../../range").Range;
var TokenIterator = require("../../token_iterator").TokenIterator;
var FoldMode = exports.FoldMode = function () { };
oop.inherits(FoldMode, BaseFoldMode);
(function () {
    this.indentKeywords = {
        "tron": 1,
        "while": 1,
        "for": 1,
        "troff": -1,
        "wend": -1,
        "next": -1
    };
    this.foldingStartMarker = /(?:\s|^)(tron|while|for)\b/i;
    this.foldingStopMarker = /(?:\b)(troff|next|wend)\b/i;
    this.getFoldWidgetRange = function (session, foldStyle, row) {
        var line = session.getLine(row);
        var isStart = this.foldingStartMarker.test(line);
        var isEnd = this.foldingStopMarker.test(line);
        if (isStart || isEnd) {
            var match = (isEnd) ? this.foldingStopMarker.exec(line) : this.foldingStartMarker.exec(line);
            var keyword = match && match[1].toLowerCase();
            if (keyword) {
                var type = session.getTokenAt(row, match.index + 2).type;
                if (type === "keyword.control")
                    return this.basicBlock(session, row, match.index + 2);
            }
        }
    };
    this.getFoldWidget = function (session, foldStyle, row) {
        var line = session.getLine(row);
        var isStart = this.foldingStartMarker.test(line);
        var isEnd = this.foldingStopMarker.test(line);
        if (isStart && !isEnd) {
            var match = this.foldingStartMarker.exec(line);
            var keyword = match && match[1].toLowerCase();
            if (keyword) {
                var type = session.getTokenAt(row, match.index + 2).type;
                if (type == "keyword.control") {
                    return "start";
                }
            }
        }
        if (foldStyle != "markbeginend" || !isEnd || isStart && isEnd)
            return "";
        var match = line.match(this.foldingStopMarker);
        var keyword = match && match[1].toLowerCase();
        if (this.indentKeywords[keyword]) {
            if (session.getTokenAt(row, match.index + 2).type === "keyword.control")
                return "end";
        }
        return "";
    };
    this.basicBlock = function (session, row, column, tokenRange) {
        var stream = new TokenIterator(session, row, column);
        var token = stream.getCurrentToken();
        if (!token || token.type != "keyword.control")
            return;
        var val = token.value.toLowerCase();
        var stack = [val];
        var dir = this.indentKeywords[val];
        if (!dir)
            return;
        var startColumn = dir === -1 ? stream.getCurrentTokenColumn() : session.getLine(row).length;
        var startRow = row;
        stream.step = dir === -1 ? stream.stepBackward : stream.stepForward;
        while (token = stream.step()) {
            val = token.value.toLowerCase();
            if (token.type !== "keyword.control" || !this.indentKeywords[val])
                continue;
            var level = dir * this.indentKeywords[val];
            if (level > 0) {
                stack.unshift(val);
            }
            else if (level <= 0) {
                stack.shift();
            }
            if (stack.length === 0) {
                break;
            }
        }
        if (!token)
            return null;
        if (tokenRange)
            return stream.getCurrentTokenRange();
        var row = stream.getCurrentTokenRow();
        if (dir === -1)
            return new Range(row, session.getLine(row).length, startRow, startColumn);
        else
            return new Range(startRow, startColumn, row, stream.getCurrentTokenColumn());
    };
}).call(FoldMode.prototype);

});

define("ace/mode/basic",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/basic_highlight_rules","ace/mode/folding/basic"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var BasicHighlightRules = require("./basic_highlight_rules").BasicHighlightRules;
var FoldMode = require("./folding/basic").FoldMode;
var Mode = function () {
    this.HighlightRules = BasicHighlightRules;
    this.foldingRules = new FoldMode();
    this.$behaviour = this.$defaultBehaviour;
    this.indentKeywords = this.foldingRules.indentKeywords;
};
oop.inherits(Mode, TextMode);
(function () {
    this.lineCommentStart = ["REM"];
    this.getMatching = function (session, row, column, tokenRange) {
        if (row == undefined) {
            var pos = session.selection.lead;
            column = pos.column;
            row = pos.row;
        }
        if (tokenRange == undefined)
            tokenRange = true;
        var startToken = session.getTokenAt(row, column);
        if (startToken) {
            var val = startToken.value.toLowerCase();
            if (val in this.indentKeywords)
                return this.foldingRules.basicBlock(session, row, column, tokenRange);
        }
    };
    this.$id = "ace/mode/basic";
}).call(Mode.prototype);
exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/basic"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            