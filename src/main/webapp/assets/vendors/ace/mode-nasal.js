define("ace/mode/nasal_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module){/* ***** BEGIN LICENSE BLOCK *****
 * Distributed under the BSD license:
 *
 * Copyright (c) 2012, Ajax.org B.V.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Ajax.org B.V. nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL AJAX.ORG B.V. BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ***** END LICENSE BLOCK ***** */
"use strict";
var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var NasalHighlightRules = function () {
    this.$rules = {
        start: [{
                token: "constant.other.allcaps.nasal",
                regex: /\b[[:upper:]_][[:upper:][:digit:]_]*\b(?![\.\(\'\"])/,
                comment: "Match identifiers in ALL_CAPS as constants, except when followed by `.`, `(`, `'`, or `\"`."
            }, {
                todo: {
                    token: [
                        "support.class.nasal",
                        "meta.function.nasal",
                        "entity.name.function.nasal",
                        "meta.function.nasal",
                        "keyword.operator.nasal",
                        "meta.function.nasal",
                        "storage.type.function.nasal",
                        "meta.function.nasal",
                        "punctuation.definition.parameters.begin.nasal"
                    ],
                    regex: /([a-zA-Z_?.$][\w?.$]*)(\.)([a-zA-Z_?.$][\w?.$]*)(\s*)(=)(\s*)(func)(\s*)(\()/,
                    push: [{
                            token: "punctuation.definition.parameters.end.nasal",
                            regex: /\)/,
                            next: "pop"
                        }, {
                            include: "$self"
                        }, {
                            token: "variable.parameter.nasal",
                            regex: /\w/
                        }, {
                            defaultToken: "meta.function.nasal"
                        }]
                },
                comment: "match stuff like: Sound.play = func() { … }"
            }, {
                todo: {
                    token: [
                        "entity.name.function.nasal",
                        "meta.function.nasal",
                        "keyword.operator.nasal",
                        "meta.function.nasal",
                        "storage.type.function.nasal",
                        "meta.function.nasal",
                        "punctuation.definition.parameters.begin.nasal"
                    ],
                    regex: /([a-zA-Z_?$][\w?$]*)(\s*)(=)(\s*)(func)(\s*)(\()/,
                    push: [{
                            token: "punctuation.definition.parameters.end.nasal",
                            regex: /\)/,
                            next: "pop"
                        }, {
                            include: "$self"
                        }, {
                            token: "variable.parameter.nasal",
                            regex: /\w/
                        }, {
                            defaultToken: "meta.function.nasal"
                        }]
                },
                comment: "match stuff like: play = func() { … }"
            }, {
                todo: {
                    token: [
                        "entity.name.function.nasal",
                        "meta.function.nasal",
                        "keyword.operator.nasal",
                        "meta.function.nasal",
                        "storage.type.function.nasal",
                        "meta.function.nasal",
                        "punctuation.definition.parameters.begin.nasal"
                    ],
                    regex: /([a-zA-Z_?$][\w?$]*)(\s*)(=)(\s*\(\s*)(func)(\s*)(\()/,
                    push: [{
                            token: "punctuation.definition.parameters.end.nasal",
                            regex: /\)/,
                            next: "pop"
                        }, {
                            include: "$self"
                        }, {
                            token: "variable.parameter.nasal",
                            regex: /\w/
                        }, {
                            defaultToken: "meta.function.nasal"
                        }]
                },
                comment: "match stuff like: play = (func() { … }"
            }, {
                todo: {
                    token: [
                        "entity.name.function.nasal",
                        "meta.function.hash.nasal",
                        "storage.type.function.nasal",
                        "meta.function.hash.nasal",
                        "punctuation.definition.parameters.begin.nasal"
                    ],
                    regex: /\b([a-zA-Z_?.$][\w?.$]*)(\s*:\s*\b)(func)(\s*)(\()/,
                    push: [{
                            token: "punctuation.definition.parameters.end.nasal",
                            regex: /\)/,
                            next: "pop"
                        }, {
                            include: "$self"
                        }, {
                            token: "variable.parameter.nasal",
                            regex: /\w/
                        }, {
                            defaultToken: "meta.function.hash.nasal"
                        }]
                },
                comment: "match stuff like: foobar: func() { … }"
            }, {
                todo: {
                    token: [
                        "storage.type.function.nasal",
                        "meta.function.nasal",
                        "punctuation.definition.parameters.begin.nasal"
                    ],
                    regex: /\b(func)(\s*)(\()/,
                    push: [{
                            token: "punctuation.definition.parameters.end.nasal",
                            regex: /\)/,
                            next: "pop"
                        }, {
                            include: "$self"
                        }, {
                            token: "variable.parameter.nasal",
                            regex: /\w/
                        }, {
                            defaultToken: "meta.function.nasal"
                        }]
                },
                comment: "match stuff like: func() { … }"
            }, {
                token: [
                    "keyword.operator.new.nasal",
                    "meta.class.instance.constructor",
                    "entity.name.type.instance.nasal"
                ],
                regex: /(new)(\s+)(\w+(?:\.\w*)?)/
            }, {
                token: "keyword.control.nasal",
                regex: /\b(?:if|else|elsif|while|for|foreach|forindex)\b/
            }, {
                token: "keyword.control.nasal",
                regex: /\b(?:break(?:\s+[A-Z]{2,16})?(?=\s*(?:;|\}))|continue(?:\s+[A-Z]{2,16})?(?=\s*(?:;|\}))|[A-Z]{2,16}(?=\s*;(?:[^\)#;]*?;){0,2}[^\)#;]*?\)))\b/
            }, {
                token: "keyword.operator.nasal",
                regex: /!|\*|\-|\+|~|\/|==|=|!=|<=|>=|<|>|!|\?|\:|\*=|\/=|\+=|\-=|~=|\.\.\.|\b(?:and|or)\b/
            }, {
                token: "variable.language.nasal",
                regex: /\b(?:me|arg|parents|obj)\b/
            }, {
                token: "storage.type.nasal",
                regex: /\b(?:return|var)\b/
            }, {
                token: "constant.language.nil.nasal",
                regex: /\bnil\b/
            }, {
                token: "punctuation.definition.string.begin.nasal",
                regex: /'/,
                push: [{
                        token: "punctuation.definition.string.end.nasal",
                        regex: /'/,
                        next: "pop"
                    }, {
                        token: "constant.character.escape.nasal",
                        regex: /\\'/
                    }, {
                        defaultToken: "string.quoted.single.nasal"
                    }],
                comment: "Single quoted strings"
            }, {
                token: "punctuation.definition.string.begin.nasal",
                regex: /"/,
                push: [{
                        token: "punctuation.definition.string.end.nasal",
                        regex: /"/,
                        next: "pop"
                    }, {
                        token: "constant.character.escape.nasal",
                        regex: /\\(?:x[\da-fA-F]{2}|[0-2][0-7]{,2}|3[0-6][0-7]?|37[0-7]?|[4-7][0-7]?|r|n|t|\\|")/
                    }, {
                        token: "constant.character.escape.nasal",
                        regex: /%(?:%|(?:\d+\$)?[+-]?(?:[ 0]|'.{1})?-?\d*(?:\.\d+)?[bcdeEufFgGosxX])/
                    }, {
                        defaultToken: "string.quoted.double.nasal"
                    }],
                comment: "Double quoted strings"
            }, {
                token: [
                    "punctuation.definition.string.begin.nasal",
                    "string.other",
                    "punctuation.definition.string.end.nasal"
                ],
                regex: /(`)(.)(`)/,
                comment: "Single-byte ASCII character constants"
            }, {
                token: [
                    "punctuation.definition.comment.nasal",
                    "comment.line.hash.nasal"
                ],
                regex: /(#)(.*$)/,
                comment: "Comments"
            }, {
                token: "constant.numeric.nasal",
                regex: /(?:(?:\b[0-9]+)?\.)?\b[0-9]+(?:[eE][-+]?[0-9]+)?\b/,
                comment: "Integers, floats, and scientific format"
            }, {
                token: "constant.numeric.nasal",
                regex: /0[x|X][0-9a-fA-F]+/,
                comment: "Hex codes"
            }, {
                token: "punctuation.terminator.statement.nasal",
                regex: /\;/
            }, {
                token: [
                    "punctuation.section.scope.begin.nasal",
                    "punctuation.section.scope.end.nasal"
                ],
                regex: /(\[)(\])/
            }, {
                todo: {
                    token: "punctuation.section.scope.begin.nasal",
                    regex: /\{/,
                    push: [{
                            token: "punctuation.section.scope.end.nasal",
                            regex: /\}/,
                            next: "pop"
                        }, {
                            include: "$self"
                        }]
                }
            }, {
                todo: {
                    token: "punctuation.section.scope.begin.nasal",
                    regex: /\(/,
                    push: [{
                            token: "punctuation.section.scope.end.nasal",
                            regex: /\)/,
                            next: "pop"
                        }, {
                            include: "$self"
                        }]
                }
            }, {
                token: "invalid.illegal",
                regex: /%|\$|@|&|\^|\||\\|`/,
                comment: "Illegal characters"
            }, {
                todo: {
                    comment: "TODO: Symbols in hash keys"
                },
                comment: "TODO: Symbols in hash keys"
            }, {
                token: "variable.language.nasal",
                regex: /\b(?:append|bind|call|caller|chr|closure|cmp|compile|contains|delete|die|find|ghosttype|id|int|keys|left|num|pop|right|setsize|size|sort|split|sprintf|streq|substr|subvec|typeof|readline)\b/,
                comment: "Core functions"
            }, {
                token: "variable.language.nasal",
                regex: /\b(?:abort|abs|aircraftToCart|addcommand|airportinfo|airwaysRoute|assert|carttogeod|cmdarg|courseAndDistance|createDiscontinuity|createViaTo|createWP|createWPFrom|defined|directory|fgcommand|findAirportsByICAO|findAirportsWithinRange|findFixesByID|findNavaidByFrequency|findNavaidsByFrequency|findNavaidsByID|findNavaidsWithinRange|finddata|flightplan|geodinfo|geodtocart|get_cart_ground_intersection|getprop|greatCircleMove|interpolate|isa|logprint|magvar|maketimer|start|stop|restart|maketimestamp|md5|navinfo|parse_markdown|parsexml|print|printf|printlog|rand|registerFlightPlanDelegate|removecommand|removelistener|resolvepath|setlistener|_setlistener|setprop|srand|systime|thisfunc|tileIndex|tilePath|values)\b/,
                comment: "FG ext core functions"
            }, {
                token: "variable.language.nasal",
                regex: /\b(?:singleShot|isRunning|simulatedTime)\b/,
                comment: "FG ext core functions"
            }, {
                token: "constant.language.nasal",
                regex: /\b(?:D2R|FPS2KT|FT2M|GAL2L|IN2M|KG2LB|KT2FPS|KT2MPS|LG2GAL|LB2KG|M2FT|M2IN|M2NM|MPS2KT|NM2M|R2D)\b/,
                comment: "FG ext core constants"
            }, {
                token: "support.function.nasal",
                regex: /\b(?:addChild|addChildren|alias|clearValue|equals|getAliasTarget|getAttribute|getBoolValue|getChild|getChildren|getIndex|getName|getNode|getParent|getPath|getType|getValue|getValues|initNode|remove|removeAllChildren|removeChild|removeChildren|setAttribute|setBoolValue|setDoubleValue|setIntValue|setValue|setValues|unalias|compileCondition|condition|copy|dump|getNode|nodeList|runBinding|setAll|wrap|wrapNode)\b/,
                comment: "FG func props"
            }, {
                token: "support.class.nasal",
                regex: /\bNode\b/,
                comment: "FG node class"
            }, {
                token: "variable.language.nasal",
                regex: /\b(?:props|globals)\b/,
                comment: "FG func props variables"
            }, {
                todo: {
                    token: [
                        "support.function.nasal",
                        "punctuation.definition.arguments.begin.nasal"
                    ],
                    regex: /\b([a-zA-Z_?$][\w?$]*)(\()/,
                    push: [{
                            token: "punctuation.definition.arguments.end.nasal",
                            regex: /\)/,
                            next: "pop"
                        }, {
                            include: "$self"
                        }, {
                            defaultToken: "meta.function-call.nasal"
                        }]
                },
                comment: "function call"
            }]
    };
    this.normalizeRules();
};
NasalHighlightRules.metaData = {
    fileTypes: ["nas"],
    name: "Nasal",
    scopeName: "source.nasal"
};
oop.inherits(NasalHighlightRules, TextHighlightRules);
exports.NasalHighlightRules = NasalHighlightRules;

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

define("ace/mode/nasal",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/nasal_highlight_rules","ace/mode/folding/cstyle"], function(require, exports, module){/* ***** BEGIN LICENSE BLOCK *****
 * Distributed under the BSD license:
 *
 * Copyright (c) 2012, Ajax.org B.V.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Ajax.org B.V. nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL AJAX.ORG B.V. BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ***** END LICENSE BLOCK ***** */
"use strict";
var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var NasalHighlightRules = require("./nasal_highlight_rules").NasalHighlightRules;
var FoldMode = require("./folding/cstyle").FoldMode;
var Mode = function () {
    this.HighlightRules = NasalHighlightRules;
    this.foldingRules = new FoldMode();
};
oop.inherits(Mode, TextMode);
(function () {
    this.$id = "ace/mode/nasal";
}).call(Mode.prototype);
exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/nasal"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            