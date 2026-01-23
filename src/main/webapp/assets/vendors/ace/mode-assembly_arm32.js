define("ace/mode/assembly_arm32_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var AssemblyARM32HighlightRules = function () {
    this.$rules = {
        start: [
            {
                token: 'keyword.control.assembly',
                regex: '\\b(?:cpsid|cpsie|cps|setend|(?:srs|rfe)(?:ia|ib|da|db|fd|ed|fa|ea)|bkpt|nop|pld|cdp2|mrc2|mrrc2|mcr2|mcrr2|ldc2|stc2|(?:add|adc|sub|sbc|rsb|rsc|mul|mla|umull|umlal|smull|smlal|mvn|and|eor|orr|bic)(?:eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?s?|(?:(?:q|qd)?(?:add|sub)|umaal|smul(?:b|t)(?:b|t)|smulw(?:b|t)|smla(?:b|t)(?:b|t)|smlaw(?:b|t)|smlal(?:b|t)(?:b|t)|smuadx?|smladx?|smlaldx?|smusdx?|smlsdx?|smlsldx?|smmulr?|smmlar?|smmlsr?|mia|miaph|mia(?:b|t)(?:b|t)|clz|(?:s|q|sh|u|uq|uh)(?:add16|sub16|add8|sub8|addsubx|subaddx)|usad8|usada8|mrs|msr|mra|mar|cpy|tst|teq|cmp|cmn|ssat|ssat16|usat|usat16|pkhbt|pkhtb|sxth|sxtb16|sxtb|uxth|uxtb16|uxtb|sxtah|sxtab16|sxtab|uxtah|uxtab16|uxtab|rev|rev16|revsh|sel|b|bl|bx|blx|bxj|swi|svc|ldrex|strex|cdp|mrc|mrrc|mcr|mcrr|ldc|stc)(?:eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?|ldr(?:eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?(?:t|b|bt|sb|h|sh|d)?|str(?:eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?(?:t|b|bt|h|d)?|(?:ldm|stm)(?:eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?(?:ia|ib|da|db|fd|ed|fa|ea)|swp(?:eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?b?|mov(?:t|w)?)\\b',
                caseInsensitive: true
            },
            {
                token: 'variable.parameter.register.assembly',
                regex: '\\b(?:r0|r1|r2|r3|r4|r5|r6|r7|r8|r9|r10|r11|r12|r13|r14|r15|fp|ip|sp|lr|pc|cpsr|spsr|c|f|s|x|lsl|lsr|asr|ror|rrx)\\b',
                caseInsensitive: true
            },
            {
                token: 'constant.character.hexadecimal.assembly',
                regex: '#0x[A-F0-9]+',
                caseInsensitive: true
            },
            {
                token: 'constant.character.decimal.assembly',
                regex: '#[0-9]+'
            },
            {
                token: 'string.assembly',
                regex: /'([^\\']|\\.)*'/
            },
            {
                token: 'string.assembly',
                regex: /"([^\\"]|\\.)*"/
            },
            {
                token: 'support.function.directive.assembly',
                regex: '(?:\.section|\.global|\.text|\.asciz|\.asciiz|\.ascii|\.align|\.byte|\.end|\.data|\.equ|\.extern|\.include)'
            },
            {
                token: 'entity.name.function.assembly',
                regex: '^\\s*%%[\\w.]+?:$'
            },
            {
                token: 'entity.name.function.assembly',
                regex: '^\\s*%\\$[\\w.]+?:$'
            },
            {
                token: 'entity.name.function.assembly',
                regex: '^[\\w.]+?:'
            },
            {
                token: 'entity.name.function.assembly',
                regex: '^[\\w.]+?\\b'
            },
            {
                token: 'comment.assembly',
                regex: '\\/\\*', next: 'comment'
            },
            {
                token: 'comment.assembly',
                regex: '(?:;|//|@).*$'
            }
        ],
        comment: [
            {
                token: 'comment.assembly',
                regex: '\\*\\/', next: 'start'
            },
            {
                defaultToken: 'comment'
            }
        ]
    };
    this.normalizeRules();
};
AssemblyARM32HighlightRules.metaData = { fileTypes: ['s'],
    name: 'Assembly ARM32',
    scopeName: 'source.assembly' };
oop.inherits(AssemblyARM32HighlightRules, TextHighlightRules);
exports.AssemblyARM32HighlightRules = AssemblyARM32HighlightRules;

});

define("ace/mode/folding/coffee",["require","exports","module","ace/lib/oop","ace/mode/folding/fold_mode","ace/range"], function(require, exports, module){"use strict";
var oop = require("../../lib/oop");
var BaseFoldMode = require("./fold_mode").FoldMode;
var Range = require("../../range").Range;
var FoldMode = exports.FoldMode = function () { };
oop.inherits(FoldMode, BaseFoldMode);
(function () {
    this.commentBlock = function (session, row) {
        var re = /\S/;
        var line = session.getLine(row);
        var startLevel = line.search(re);
        if (startLevel == -1 || line[startLevel] != "#")
            return;
        var startColumn = line.length;
        var maxRow = session.getLength();
        var startRow = row;
        var endRow = row;
        while (++row < maxRow) {
            line = session.getLine(row);
            var level = line.search(re);
            if (level == -1)
                continue;
            if (line[level] != "#")
                break;
            endRow = row;
        }
        if (endRow > startRow) {
            var endColumn = session.getLine(endRow).length;
            return new Range(startRow, startColumn, endRow, endColumn);
        }
    };
    this.getFoldWidgetRange = function (session, foldStyle, row) {
        var range = this.indentationBlock(session, row);
        if (range)
            return range;
        range = this.commentBlock(session, row);
        if (range)
            return range;
    };
    this.getFoldWidget = function (session, foldStyle, row) {
        var line = session.getLine(row);
        var indent = line.search(/\S/);
        var next = session.getLine(row + 1);
        var prev = session.getLine(row - 1);
        var prevIndent = prev.search(/\S/);
        var nextIndent = next.search(/\S/);
        if (indent == -1) {
            session.foldWidgets[row - 1] = prevIndent != -1 && prevIndent < nextIndent ? "start" : "";
            return "";
        }
        if (prevIndent == -1) {
            if (indent == nextIndent && line[indent] == "#" && next[indent] == "#") {
                session.foldWidgets[row - 1] = "";
                session.foldWidgets[row + 1] = "";
                return "start";
            }
        }
        else if (prevIndent == indent && line[indent] == "#" && prev[indent] == "#") {
            if (session.getLine(row - 2).search(/\S/) == -1) {
                session.foldWidgets[row - 1] = "start";
                session.foldWidgets[row + 1] = "";
                return "";
            }
        }
        if (prevIndent != -1 && prevIndent < indent)
            session.foldWidgets[row - 1] = "start";
        else
            session.foldWidgets[row - 1] = "";
        if (indent < nextIndent)
            return "start";
        else
            return "";
    };
}).call(FoldMode.prototype);

});

define("ace/mode/assembly_arm32",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/assembly_arm32_highlight_rules","ace/mode/folding/coffee"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var AssemblyARM32HighlightRules = require("./assembly_arm32_highlight_rules").AssemblyARM32HighlightRules;
var FoldMode = require("./folding/coffee").FoldMode;
var Mode = function () {
    this.HighlightRules = AssemblyARM32HighlightRules;
    this.foldingRules = new FoldMode();
    this.$behaviour = this.$defaultBehaviour;
};
oop.inherits(Mode, TextMode);
(function () {
    this.lineCommentStart = [";"];
    this.blockComment = { start: "/*", end: "*/" };
    this.$id = "ace/mode/assembly_arm32";
}).call(Mode.prototype);
exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/assembly_arm32"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            