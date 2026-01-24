define("ace/mode/json_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var JsonHighlightRules = function () {
    this.$rules = {
        "start": [
            {
                token: "variable", // single line
                regex: '["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]\\s*(?=:)'
            }, {
                token: "string", // single line
                regex: '"',
                next: "string"
            }, {
                token: "constant.numeric", // hex
                regex: "0[xX][0-9a-fA-F]+\\b"
            }, {
                token: "constant.numeric", // float
                regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
            }, {
                token: "constant.language.boolean",
                regex: "(?:true|false)\\b"
            }, {
                token: "text", // single quoted strings are not allowed
                regex: "['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']"
            }, {
                token: "comment", // comments are not allowed, but who cares?
                regex: "\\/\\/.*$"
            }, {
                token: "comment.start", // comments are not allowed, but who cares?
                regex: "\\/\\*",
                next: "comment"
            }, {
                token: "paren.lparen",
                regex: "[[({]"
            }, {
                token: "paren.rparen",
                regex: "[\\])}]"
            }, {
                token: "punctuation.operator",
                regex: /[,]/
            }, {
                token: "text",
                regex: "\\s+"
            }
        ],
        "string": [
            {
                token: "constant.language.escape",
                regex: /\\(?:x[0-9a-fA-F]{2}|u[0-9a-fA-F]{4}|["\\\/bfnrt])/
            }, {
                token: "string",
                regex: '"|$',
                next: "start"
            }, {
                defaultToken: "string"
            }
        ],
        "comment": [
            {
                token: "comment.end", // comments are not allowed, but who cares?
                regex: "\\*\\/",
                next: "start"
            }, {
                defaultToken: "comment"
            }
        ]
    };
};
oop.inherits(JsonHighlightRules, TextHighlightRules);
exports.JsonHighlightRules = JsonHighlightRules;

});

define("ace/ext/simple_tokenizer",["require","exports","module","ace/ext/simple_tokenizer","ace/mode/json_highlight_rules","ace/tokenizer","ace/layer/text_util"], function(require, exports, module){/**
 * ## Simple tokenizer extension
 *
 * Provides standalone tokenization functionality that can parse code content using Ace's highlight rules without
 * requiring a full editor instance. This is useful for generating syntax-highlighted tokens for external rendering,
 * static code generation, or testing tokenization rules. The tokenizer processes text line by line and returns
 * structured token data with CSS class names compatible with Ace themes.
 *
 * **Usage:**
 * ```javascript
 * const { tokenize } = require("ace/ext/simple_tokenizer");
 * const { JsonHighlightRules } = require("ace/mode/json_highlight_rules");
 *
 * const content = '{"name": "value"}';
 * const tokens = tokenize(content, new JsonHighlightRules());
 * // Returns: [[{className: "ace_paren ace_lparen", value: "{"}, ...]]
 * ```
 *
 * @module
 */
"use strict";
var Tokenizer = require("../tokenizer").Tokenizer;
var isTextToken = require("../layer/text_util").isTextToken;
var SimpleTokenizer = /** @class */ (function () {
    function SimpleTokenizer(content, tokenizer) {
        this._lines = content.split(/\r\n|\r|\n/);
        this._states = [];
        this._tokenizer = tokenizer;
    }
    SimpleTokenizer.prototype.getTokens = function (row) {
        var line = this._lines[row];
        var previousState = this._states[row - 1];
        var data = this._tokenizer.getLineTokens(line, previousState);
        this._states[row] = data.state;
        return data.tokens;
    };
    SimpleTokenizer.prototype.getLength = function () {
        return this._lines.length;
    };
    return SimpleTokenizer;
}());
function tokenize(content, highlightRules) {
    var tokenizer = new SimpleTokenizer(content, new Tokenizer(highlightRules.getRules()));
    var result = [];
    for (var lineIndex = 0; lineIndex < tokenizer.getLength(); lineIndex++) {
        var lineTokens = tokenizer.getTokens(lineIndex);
        result.push(lineTokens.map(function (token) { return ({
            className: isTextToken(token.type) ? undefined : "ace_" + token.type.replace(/\./g, " ace_"),
            value: token.value
        }); }));
    }
    return result;
}
exports.tokenize = tokenize;

});                (function() {
                    window.require(["ace/ext/simple_tokenizer"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            