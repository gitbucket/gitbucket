define("ace/mode/zig_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var ZigHighlightRules = function () {
    this.$rules = {
        start: [{
                include: "#dummy_main"
            }],
        "#block": [{
                token: [
                    "storage.type.zig",
                    "text",
                    "punctuation.section.braces.begin.zig"
                ],
                regex: /((?:[a-zA-Z_][\w.]*|@\".+\")?)(\s*)(\{)/,
                push: [{
                        token: "punctuation.section.braces.end.zig",
                        regex: /\}/,
                        next: "pop"
                    }, {
                        include: "#dummy_main"
                    }]
            }],
        "#character_escapes": [{
                token: "constant.character.escape.newline.zig",
                regex: /\\n/
            }, {
                token: "constant.character.escape.carrigereturn.zig",
                regex: /\\r/
            }, {
                token: "constant.character.escape.tabulator.zig",
                regex: /\\t/
            }, {
                token: "constant.character.escape.backslash.zig",
                regex: /\\\\/
            }, {
                token: "constant.character.escape.single-quote.zig",
                regex: /\\'/
            }, {
                token: "constant.character.escape.double-quote.zig",
                regex: /\\\"/
            }, {
                token: "constant.character.escape.hexidecimal.zig",
                regex: /\\x[a-fA-F\d]{2}/
            }, {
                token: "constant.character.escape.hexidecimal.zig",
                regex: /\\u\{[a-fA-F\d]{1,6}\}/
            }],
        "#comments": [{
                token: "comment.line.documentation.zig",
                regex: /\/\/[!\/](?=[^\/])/,
                push: [{
                        token: "comment.line.documentation.zig",
                        regex: /$/,
                        next: "pop"
                    }, {
                        include: "#commentContents"
                    }, {
                        defaultToken: "comment.line.documentation.zig"
                    }]
            }, {
                token: "comment.line.double-slash.zig",
                regex: /\/\//,
                push: [{
                        token: "comment.line.double-slash.zig",
                        regex: /$/,
                        next: "pop"
                    }, {
                        include: "#commentContents"
                    }, {
                        defaultToken: "comment.line.double-slash.zig"
                    }]
            }],
        "#commentContents": [{
                token: "keyword.todo.zig",
                regex: /\b(?:TODO|FIXME|XXX|NOTE)\b:?/
            }],
        "#constants": [{
                token: "constant.language.zig",
                regex: /\b(?:null|undefined|true|false)\b/
            }, {
                token: "constant.numeric.integer.zig",
                regex: /\b(?!\.)-?[\d_]+(?!\.)\b/
            }, {
                token: "constant.numeric.integer.hexadecimal.zig",
                regex: /\b(?!\.)0x[a-fA-F\d_]+(?!\.)\b/
            }, {
                token: "constant.numeric.integer.octal.zig",
                regex: /\b(?!\.)0o[0-7_]+(?!\.)\b/
            }, {
                token: "constant.numeric.integer.binary.zig",
                regex: /\b(?!\.)0b[01_]+(?!\.)\b/
            }, {
                token: "constant.numeric.float.zig",
                regex: /(?!\.)-?\b[\d_]+(?:\.[\d_]+)?(?:[eE][+-]?[\d_]+)?(?!\.)\b/
            }, {
                token: "constant.numeric.float.hexadecimal.zig",
                regex: /(?!\.)-?\b0x[a-fA-F\d_]+(?:\.[a-fA-F\d_]+)?[pP]?(?:[+-]?[\d_]+)?(?!\.)\b/
            }],
        "#container_decl": [{
                token: "entity.name.union.zig",
                regex: /\b(?!\d)(?:[a-zA-Z_]\w*|@\".+\")?(?=\s*=\s*(?:extern|packed)?\b\s*union\s*[(\{])/
            }, {
                token: "entity.name.struct.zig",
                regex: /\b(?!\d)(?:[a-zA-Z_]\w*|@\".+\")?(?=\s*=\s*(?:extern|packed)?\b\s*struct\s*[(\{])/
            }, {
                token: "entity.name.enum.zig",
                regex: /\b(?!\d)(?:[a-zA-Z_]\w*|@\".+\")?(?=\s*=\s*(?:extern|packed)?\b\s*enum\s*[(\{])/
            }, {
                token: "entity.name.error.zig",
                regex: /\b(?!\d)(?:[a-zA-Z_]\w*|@\".+\")?(?=\s*=\s*error\s*[(\{])/
            }, {
                token: [
                    "storage.type.error.zig",
                    "punctuation.accessor.zig",
                    "entity.name.error.zig"
                ],
                regex: /\b(error)(\.)([a-zA-Z_]\w*|@\".+\")/
            }],
        "#dummy_main": [{
                include: "#label"
            }, {
                include: "#function_type"
            }, {
                include: "#function_def"
            }, {
                include: "#punctuation"
            }, {
                include: "#storage_modifier"
            }, {
                include: "#container_decl"
            }, {
                include: "#constants"
            }, {
                include: "#comments"
            }, {
                include: "#strings"
            }, {
                include: "#storage"
            }, {
                include: "#keywords"
            }, {
                include: "#operators"
            }, {
                include: "#support"
            }, {
                include: "#field_decl"
            }, {
                include: "#block"
            }, {
                include: "#function_call"
            }, {
                include: "#enum_literal"
            }, {
                include: "#variables"
            }],
        "#enum_literal": [{
                token: "constant.language.enum",
                regex: /(?!\w|\)|\?|\}|\]|\*|\.)\.(?:[a-zA-Z_]\w*\b|@\"[^\"]*\")(?!\(|\s*=[^=>])/
            }],
        "#field_decl": [{
                token: [
                    "variable.other.member.zig",
                    "text",
                    "punctuation.separator.zig",
                    "text"
                ],
                regex: /([a-zA-Z_]\w*|@\".+\")(\s*)(:)(\s*)/,
                push: [{
                        token: [
                            "storage.type.zig",
                            "text",
                            "punctuation.separator.zig",
                            "keyword.operator.assignment.zig"
                        ],
                        regex: /((?:[a-zA-Z_][\w.]*|@\".+\")?)(\s*)(?:(,)|(=)|$)/,
                        next: "pop"
                    }, {
                        include: "#dummy_main"
                    }]
            }],
        "#function_call": [{
                token: "variable.function.zig",
                regex: /\b(?!fn)(?:[a-zA-Z_]\w*|@\".+\")(?=\s*\()/
            }],
        "#keywords": [{
                token: "keyword.control.zig",
                regex: /\b(?:while|for|break|return|continue|asm|defer|errdefer|unreachable)\b/
            }, {
                token: "keyword.control.async.zig",
                regex: /\b(?:async|await|suspend|nosuspend|resume)\b/
            }, {
                token: "keyword.control.conditional.zig",
                regex: /\b(?:if|else|switch|try|catch|orelse)\b/
            }, {
                token: "keyword.control.import.zig",
                regex: /\b(?!\w)(?:@import|@cImport|@cInclude)\b/
            }, {
                token: "keyword.other.usingnamespace.zig",
                regex: /\busingnamespace\b/
            }],
        "#label": [{
                token: [
                    "keyword.control.zig",
                    "text",
                    "entity.name.label.zig",
                    "entity.name.label.zig"
                ],
                regex: /\b(break|continue)(\s*:\s*)([a-zA-Z_]\w*|@\".+\")\b|\b(?!\d)([a-zA-Z_]\w*|@\".+\")\b(?=\s*:\s*(?:\{|while\b))/
            }],
        "#operators": [{
                token: "keyword.operator.zig",
                regex: /\b!\b/
            }, {
                token: "keyword.operator.logical.zig",
                regex: /==|(?:!|>|<)=?/
            }, {
                token: "keyword.operator.word.zig",
                regex: /\b(?:and|or)\b/
            }, {
                token: "keyword.operator.assignment.zig",
                regex: /(?:(?:\+|-|\*)\%?|\/|%|<<|>>|&|\|(?=[^\|])|\^)?=/
            }, {
                token: "keyword.operator.arithmetic.zig",
                regex: /(?:\+|-|\*)\%?|\/(?!\/)|%/
            }, {
                token: "keyword.operator.bitwise.zig",
                regex: /<<|>>|&(?=[a-zA-Z_]|@\")|\|(?=[^\|])|\^|~/
            }, {
                token: "keyword.operator.other.zig",
                regex: /\+\+|\*\*|->|\.\?|\.\*|&(?=[a-zA-Z_]|@\")|\?|\|\||\.{2,3}/
            }],
        "#param_list": [{
                token: [
                    "variable.parameter.zig",
                    "text",
                    "punctuation.separator.zig",
                    "text"
                ],
                regex: /([a-zA-Z_]\w*|@\".+\")(\s*)(:)(\s*)/,
                push: [{
                        token: [
                            "storage.type.zig",
                            "text",
                            "punctuation.separator.zig",
                            "punctuation.section.parens.end.zig"
                        ],
                        regex: /((?:[a-zA-Z_][\w.]*|@\".+\")?)(\s*)(?:(,)|(\)))/,
                        next: "pop"
                    }, {
                        include: "#dummy_main"
                    }, {
                        token: "storage.type.zig",
                        regex: /[a-zA-Z_][\w.]*|@\".+\"/
                    }]
            }],
        "#punctuation": [{
                token: "punctuation.separator.zig",
                regex: /,/
            }, {
                token: "punctuation.terminator.zig",
                regex: /;/
            }, {
                token: "punctuation.section.parens.begin.zig",
                regex: /\(/
            }, {
                token: "punctuation.section.parens.end.zig",
                regex: /\)/
            }],
        "#storage": [{
                token: "storage.type.zig",
                regex: /\b(?:bool|void|noreturn|type|anyerror|anytype)\b/
            }, {
                token: "storage.type.integer.zig",
                regex: /\b(?!\.)(?:[iu]\d+|[iu]size|comptime_int)\b/
            }, {
                token: "storage.type.float.zig",
                regex: /\b(?:f16|f32|f64|f128|comptime_float)\b/
            }, {
                token: "storage.type.c_compat.zig",
                regex: /\b(?:c_short|c_ushort|c_int|c_uint|c_long|c_ulong|c_longlong|c_ulonglong|c_longdouble|c_void)\b/
            }, {
                token: [
                    "storage.type.zig",
                    "text",
                    "keyword.operator.zig",
                    "text",
                    "storage.type.zig"
                ],
                regex: /\b(anyframe)\b(\s*)((?:->)?)(\s*)(?:([a-zA-Z_][\w.]*|@\".+\")\b(?!\s*\())?/
            }, {
                token: "storage.type.function.zig",
                regex: /\bfn\b/
            }, {
                token: "storage.type.test.zig",
                regex: /\btest\b/
            }, {
                token: "storage.type.struct.zig",
                regex: /\bstruct\b/
            }, {
                token: "storage.type.enum.zig",
                regex: /\benum\b/
            }, {
                token: "storage.type.union.zig",
                regex: /\bunion\b/
            }, {
                token: "storage.type.error.zig",
                regex: /\berror\b/
            }],
        "#storage_modifier": [{
                token: "storage.modifier.zig",
                regex: /\b(?:const|var|extern|packed|export|pub|noalias|inline|noinline|comptime|volatile|align|linksection|threadlocal|allowzero)\b/
            }],
        "#strings": [{
                token: "string.quoted.single.zig",
                regex: /\'/,
                push: [{
                        token: "string.quoted.single.zig",
                        regex: /\'/,
                        next: "pop"
                    }, {
                        include: "#character_escapes"
                    }, {
                        token: "invalid.illegal.character.zig",
                        regex: /\\[^\'][^\']*?/
                    }, {
                        defaultToken: "string.quoted.single.zig"
                    }]
            }, {
                token: "string.quoted.double.zig",
                regex: /c?\"/,
                push: [{
                        token: "string.quoted.double.zig",
                        regex: /\"/,
                        next: "pop"
                    }, {
                        include: "#character_escapes"
                    }, {
                        token: "invalid.illegal.character.zig",
                        regex: /\\[^\'][^\']*?/
                    }, {
                        defaultToken: "string.quoted.double.zig"
                    }]
            }, {
                token: "string.quoted.other.zig",
                regex: /c?\\\\/,
                push: [{
                        token: "string.quoted.other.zig",
                        regex: /$/,
                        next: "pop"
                    }, {
                        defaultToken: "string.quoted.other.zig"
                    }]
            }],
        "#function_type": [{
                token: [
                    "storage.type.function.zig",
                    "text",
                    "punctuation.section.parens.begin.zig"
                ],
                regex: /\b(fn)(\s*)(\()/,
                push: [{
                        token: [
                            "text",
                            "storage.type.zig",
                            "text",
                            "keyword.operator.zig",
                            "text",
                            "storage.type.zig"
                        ],
                        regex: /(\s*)(?:([a-zA-Z_]\w*|@\".+\"))?(\s*)((?:!)?)(\s*)([a-zA-Z_]\w*|@\".+\")/,
                        next: "pop"
                    }, {
                        include: "#label"
                    }, {
                        include: "#param_list"
                    }, {
                        token: "storage.type.zig",
                        regex: /[a-zA-Z_]\w*|@\".+\"/
                    }, {
                        include: "#dummy_main"
                    }, {
                        defaultToken: "meta.function.parameters.zig"
                    }]
            }],
        "#function_def": [{
                token: [
                    "text",
                    "entity.name.function",
                    "punctuation.section.parens.begin.zig"
                ],
                regex: /(?=fn\s+)(\s+)([a-zA-Z_]\w*|@\".+\")(\()/,
                push: [{
                        token: [
                            "text",
                            "storage.type.zig",
                            "keyword.operator.zig",
                            "text",
                            "storage.type.zig"
                        ],
                        regex: /(\s*)((?:[a-zA-Z_][\w.]*|@\".+\")?)((?:!)?)(\s*)(?:([a-zA-Z_][\w.]*|@\".+\")?)/,
                        next: "pop"
                    }, {
                        include: "#label"
                    }, {
                        include: "#param_list"
                    }, {
                        token: "storage.type.zig",
                        regex: /[a-zA-Z_][\w.]*|@\".+\"/
                    }, {
                        include: "#dummy_main"
                    }]
            }],
        "#support": [{
                token: "support.function.zig",
                regex: /\b@(?!\w|\"|[0-9])[a-zA-Z_]\w*\b/
            }],
        "#variables": [{
                token: "variable.constant.zig",
                regex: /\b[_A-Z][_A-Z0-9]+\b/
            }, {
                token: "entity.name.type.zig",
                regex: /\b[_a-zA-Z][_a-zA-Z0-9]*_t\b/
            }, {
                token: "entity.name.type.zig",
                regex: /\b[A-Z][a-zA-Z0-9]*\b/
            }, {
                token: "variable.zig",
                regex: /\b[_a-zA-Z][_a-zA-Z0-9]*\b/
            }]
    };
    this.normalizeRules();
};
ZigHighlightRules.metaData = {
    fileTypes: ["zig"],
    keyEquivalent: "^~Z",
    name: "Zig",
    scopeName: "source.zig"
};
oop.inherits(ZigHighlightRules, TextHighlightRules);
exports.ZigHighlightRules = ZigHighlightRules;

});

define("ace/mode/folding/cstyle",["require","exports","module","ace/lib/oop","ace/range","ace/mode/folding/fold_mode"], function(require, exports, module){"use strict";
var oop = require("../../lib/oop");
var Range = require("../../range").Range;
var BaseFoldMode = require("./fold_mode").FoldMode;
var FoldMode = exports.FoldMode = function (commentRegex) {
    if (commentRegex) {
        this.foldingStartMarker = new RegExp(this.foldingStartMarker.source.replace(/\|[^|]*?$/, "|" + commentRegex.start));
        this.foldingStopMarker = new RegExp(this.foldingStopMarker.source.replace(/\|[^|]*?$/, "|" + commentRegex.end));
    }
};
oop.inherits(FoldMode, BaseFoldMode);
(function () {
    this.foldingStartMarker = /([\{\[\(])[^\}\]\)]*$|^\s*(\/\*)/;
    this.foldingStopMarker = /^[^\[\{\(]*([\}\]\)])|^[\s\*]*(\*\/)/;
    this.singleLineBlockCommentRe = /^\s*(\/\*).*\*\/\s*$/;
    this.tripleStarBlockCommentRe = /^\s*(\/\*\*\*).*\*\/\s*$/;
    this.startRegionRe = /^\s*(\/\*|\/\/)#?region\b/;
    this._getFoldWidgetBase = this.getFoldWidget;
    this.getFoldWidget = function (session, foldStyle, row) {
        var line = session.getLine(row);
        if (this.singleLineBlockCommentRe.test(line)) {
            if (!this.startRegionRe.test(line) && !this.tripleStarBlockCommentRe.test(line))
                return "";
        }
        var fw = this._getFoldWidgetBase(session, foldStyle, row);
        if (!fw && this.startRegionRe.test(line))
            return "start"; // lineCommentRegionStart
        return fw;
    };
    this.getFoldWidgetRange = function (session, foldStyle, row, forceMultiline) {
        var line = session.getLine(row);
        if (this.startRegionRe.test(line))
            return this.getCommentRegionBlock(session, line, row);
        var match = line.match(this.foldingStartMarker);
        if (match) {
            var i = match.index;
            if (match[1])
                return this.openingBracketBlock(session, match[1], row, i);
            var range = session.getCommentFoldRange(row, i + match[0].length, 1);
            if (range && !range.isMultiLine()) {
                if (forceMultiline) {
                    range = this.getSectionRange(session, row);
                }
                else if (foldStyle != "all")
                    range = null;
            }
            return range;
        }
        if (foldStyle === "markbegin")
            return;
        var match = line.match(this.foldingStopMarker);
        if (match) {
            var i = match.index + match[0].length;
            if (match[1])
                return this.closingBracketBlock(session, match[1], row, i);
            return session.getCommentFoldRange(row, i, -1);
        }
    };
    this.getSectionRange = function (session, row) {
        var line = session.getLine(row);
        var startIndent = line.search(/\S/);
        var startRow = row;
        var startColumn = line.length;
        row = row + 1;
        var endRow = row;
        var maxRow = session.getLength();
        while (++row < maxRow) {
            line = session.getLine(row);
            var indent = line.search(/\S/);
            if (indent === -1)
                continue;
            if (startIndent > indent)
                break;
            var subRange = this.getFoldWidgetRange(session, "all", row);
            if (subRange) {
                if (subRange.start.row <= startRow) {
                    break;
                }
                else if (subRange.isMultiLine()) {
                    row = subRange.end.row;
                }
                else if (startIndent == indent) {
                    break;
                }
            }
            endRow = row;
        }
        return new Range(startRow, startColumn, endRow, session.getLine(endRow).length);
    };
    this.getCommentRegionBlock = function (session, line, row) {
        var startColumn = line.search(/\s*$/);
        var maxRow = session.getLength();
        var startRow = row;
        var re = /^\s*(?:\/\*|\/\/|--)#?(end)?region\b/;
        var depth = 1;
        while (++row < maxRow) {
            line = session.getLine(row);
            var m = re.exec(line);
            if (!m)
                continue;
            if (m[1])
                depth--;
            else
                depth++;
            if (!depth)
                break;
        }
        var endRow = row;
        if (endRow > startRow) {
            return new Range(startRow, startColumn, endRow, line.length);
        }
    };
}).call(FoldMode.prototype);

});

define("ace/mode/zig",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/zig_highlight_rules","ace/mode/folding/cstyle"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var ZigHighlightRules = require("./zig_highlight_rules").ZigHighlightRules;
var FoldMode = require("./folding/cstyle").FoldMode;
var Mode = function () {
    this.HighlightRules = ZigHighlightRules;
    this.foldingRules = new FoldMode();
    this.$behaviour = this.$defaultBehaviour;
};
oop.inherits(Mode, TextMode);
(function () {
    this.lineCommentStart = "//";
    this.$id = "ace/mode/zig";
}).call(Mode.prototype);
exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/zig"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            