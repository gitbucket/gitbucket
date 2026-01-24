define("ace/mode/doc_comment_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var DocCommentHighlightRules = function () {
    this.$rules = {
        "start": [
            {
                token: "comment.doc.tag",
                regex: "@\\w+(?=\\s|$)"
            }, DocCommentHighlightRules.getTagRule(), {
                defaultToken: "comment.doc.body",
                caseInsensitive: true
            }
        ]
    };
};
oop.inherits(DocCommentHighlightRules, TextHighlightRules);
DocCommentHighlightRules.getTagRule = function (start) {
    return {
        token: "comment.doc.tag.storage.type",
        regex: "\\b(?:TODO|FIXME|XXX|HACK)\\b"
    };
};
DocCommentHighlightRules.getStartRule = function (start) {
    return {
        token: "comment.doc", // doc comment
        regex: /\/\*\*(?!\/)/,
        next: start
    };
};
DocCommentHighlightRules.getEndRule = function (start) {
    return {
        token: "comment.doc", // closing comment
        regex: "\\*\\/",
        next: start
    };
};
exports.DocCommentHighlightRules = DocCommentHighlightRules;

});

define("ace/mode/java_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/doc_comment_highlight_rules","ace/mode/text_highlight_rules"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var DocCommentHighlightRules = require("./doc_comment_highlight_rules").DocCommentHighlightRules;
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var JavaHighlightRules = function () {
    var identifierRe = "[a-zA-Z_$][a-zA-Z0-9_$]*";
    var keywords = ("abstract|continue|for|new|switch|" +
        "assert|default|goto|package|synchronized|" +
        "boolean|do|if|private|this|" +
        "break|double|implements|protected|throw|" +
        "byte|else|import|public|throws|" +
        "case|enum|instanceof|return|transient|" +
        "catch|extends|int|short|try|" +
        "char|final|interface|static|void|" +
        "class|finally|long|strictfp|volatile|" +
        "const|float|native|super|while|" +
        "yield|when|record|var|" +
        "permits|(?:non\\-)?sealed");
    var buildinConstants = ("null|Infinity|NaN|undefined");
    var langClasses = ("AbstractMethodError|AssertionError|ClassCircularityError|" +
        "ClassFormatError|Deprecated|EnumConstantNotPresentException|" +
        "ExceptionInInitializerError|IllegalAccessError|" +
        "IllegalThreadStateException|InstantiationError|InternalError|" +
        "NegativeArraySizeException|NoSuchFieldError|Override|Process|" +
        "ProcessBuilder|SecurityManager|StringIndexOutOfBoundsException|" +
        "SuppressWarnings|TypeNotPresentException|UnknownError|" +
        "UnsatisfiedLinkError|UnsupportedClassVersionError|VerifyError|" +
        "InstantiationException|IndexOutOfBoundsException|" +
        "ArrayIndexOutOfBoundsException|CloneNotSupportedException|" +
        "NoSuchFieldException|IllegalArgumentException|NumberFormatException|" +
        "SecurityException|Void|InheritableThreadLocal|IllegalStateException|" +
        "InterruptedException|NoSuchMethodException|IllegalAccessException|" +
        "UnsupportedOperationException|Enum|StrictMath|Package|Compiler|" +
        "Readable|Runtime|StringBuilder|Math|IncompatibleClassChangeError|" +
        "NoSuchMethodError|ThreadLocal|RuntimePermission|ArithmeticException|" +
        "NullPointerException|Long|Integer|Short|Byte|Double|Number|Float|" +
        "Character|Boolean|StackTraceElement|Appendable|StringBuffer|" +
        "Iterable|ThreadGroup|Runnable|Thread|IllegalMonitorStateException|" +
        "StackOverflowError|OutOfMemoryError|VirtualMachineError|" +
        "ArrayStoreException|ClassCastException|LinkageError|" +
        "NoClassDefFoundError|ClassNotFoundException|RuntimeException|" +
        "Exception|ThreadDeath|Error|Throwable|System|ClassLoader|" +
        "Cloneable|Class|CharSequence|Comparable|String|Object");
    var keywordMapper = this.createKeywordMapper({
        "variable.language": "this",
        "constant.language": buildinConstants,
        "support.function": langClasses
    }, "identifier");
    this.$rules = {
        "start": [
            { include: "comments" },
            { include: "multiline-strings" },
            { include: "strings" },
            { include: "constants" },
            {
                regex: "(open(?:\\s+))?module(?=\\s*\\w)",
                token: "keyword",
                next: [{
                        regex: "{",
                        token: "paren.lparen",
                        push: [
                            {
                                regex: "}",
                                token: "paren.rparen",
                                next: "pop"
                            },
                            { include: "comments" },
                            {
                                regex: "\\b(requires|transitive|exports|opens|to|uses|provides|with)\\b",
                                token: "keyword"
                            }
                        ]
                    }, {
                        token: "text",
                        regex: "\\s+"
                    }, {
                        token: "identifier",
                        regex: "\\w+"
                    }, {
                        token: "punctuation.operator",
                        regex: "."
                    }, {
                        token: "text",
                        regex: "\\s+"
                    }, {
                        regex: "", // exit if there is anything else
                        next: "start"
                    }]
            },
            { include: "statements" }
        ],
        "comments": [
            {
                token: "comment",
                regex: "\\/\\/.*$"
            },
            {
                token: "comment.doc", // doc comment
                regex: /\/\*\*(?!\/)/,
                push: "doc-start"
            },
            {
                token: "comment", // multi line comment
                regex: "\\/\\*",
                push: [
                    {
                        token: "comment", // closing comment
                        regex: "\\*\\/",
                        next: "pop"
                    }, {
                        defaultToken: "comment"
                    }
                ]
            },
        ],
        "strings": [
            {
                token: ["punctuation", "string"],
                regex: /(\.)(")/,
                push: [
                    {
                        token: "lparen",
                        regex: /\\\{/,
                        push: [
                            {
                                token: "text",
                                regex: /$/,
                                next: "start"
                            }, {
                                token: "rparen",
                                regex: /}/,
                                next: "pop"
                            }, {
                                include: "strings"
                            }, {
                                include: "constants"
                            }, {
                                include: "statements"
                            }
                        ]
                    }, {
                        token: "string",
                        regex: /"/,
                        next: "pop"
                    }, {
                        defaultToken: "string"
                    }
                ]
            }, {
                token: "string", // single line
                regex: '["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]'
            }, {
                token: "string", // single line
                regex: "['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']"
            }
        ],
        "multiline-strings": [
            {
                token: ["punctuation", "string"],
                regex: /(\.)(""")/,
                push: [
                    {
                        token: "string",
                        regex: '"""',
                        next: "pop"
                    }, {
                        token: "lparen",
                        regex: /\\\{/,
                        push: [
                            {
                                token: "text",
                                regex: /$/,
                                next: "start"
                            }, {
                                token: "rparen",
                                regex: /}/,
                                next: "pop"
                            }, {
                                include: "multiline-strings"
                            }, {
                                include: "strings"
                            }, {
                                include: "constants"
                            }, {
                                include: "statements"
                            }
                        ]
                    }, {
                        token: "constant.language.escape",
                        regex: /\\./
                    }, {
                        defaultToken: "string"
                    }
                ]
            },
            {
                token: "string",
                regex: '"""',
                push: [
                    {
                        token: "string",
                        regex: '"""',
                        next: "pop"
                    }, {
                        token: "constant.language.escape",
                        regex: /\\./
                    }, {
                        defaultToken: "string"
                    }
                ]
            }
        ],
        "constants": [
            {
                token: "constant.numeric", // hex
                regex: /0(?:[xX][0-9a-fA-F][0-9a-fA-F_]*|[bB][01][01_]*)[LlSsDdFfYy]?\b/
            }, {
                token: "constant.numeric", // float
                regex: /[+-]?\d[\d_]*(?:(?:\.[\d_]*)?(?:[eE][+-]?[\d_]+)?)?[LlSsDdFfYy]?\b/
            }, {
                token: "constant.language.boolean",
                regex: "(?:true|false)\\b"
            }
        ],
        "statements": [
            {
                token: ["keyword", "text", "identifier"],
                regex: "(record)(\\s+)(" + identifierRe + ")\\b"
            },
            {
                token: "keyword",
                regex: "(?:" + keywords + ")\\b"
            }, {
                token: "storage.type.annotation",
                regex: "@" + identifierRe + "\\b"
            }, {
                token: "entity.name.function",
                regex: identifierRe + "(?=\\()"
            }, {
                token: keywordMapper, // TODO: Unicode escape sequences
                regex: identifierRe + "\\b"
            }, {
                token: "keyword.operator",
                regex: "!|\\$|%|&|\\||\\^|\\*|\\/|\\-\\-|\\-|\\+\\+|\\+|~|===|==|=|!=|!==|<=|>=|<<=|>>=|>>>=|<>|<|>|!|&&|\\|\\||\\?|\\:|\\*=|\\/=|%=|\\+=|\\-=|&=|\\|=|\\^=|\\b(?:in|instanceof|new|delete|typeof|void)"
            }, {
                token: "lparen",
                regex: "[[({]"
            }, {
                token: "rparen",
                regex: "[\\])}]"
            }, {
                token: "text",
                regex: "\\s+"
            }
        ]
    };
    this.embedRules(DocCommentHighlightRules, "doc-", [DocCommentHighlightRules.getEndRule("pop")]);
    this.normalizeRules();
};
oop.inherits(JavaHighlightRules, TextHighlightRules);
exports.JavaHighlightRules = JavaHighlightRules;

});

define("ace/mode/drools_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules","ace/mode/java_highlight_rules","ace/mode/doc_comment_highlight_rules"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var JavaHighlightRules = require("./java_highlight_rules").JavaHighlightRules;
var DocCommentHighlightRules = require("./doc_comment_highlight_rules").DocCommentHighlightRules;
var identifierRe = "[a-zA-Z\\$_\u00a1-\uffff][a-zA-Z\\d\\$_\u00a1-\uffff]*";
var packageIdentifierRe = "[a-zA-Z\\$_\u00a1-\uffff][\\.a-zA-Z\\d\\$_\u00a1-\uffff]*";
var DroolsHighlightRules = function () {
    var keywords = ("date|effective|expires|lock|on|active|no|loop|auto|focus" +
        "|activation|group|agenda|ruleflow|duration|timer|calendars|refract|direct" +
        "|dialect|salience|enabled|attributes|extends|template" +
        "|function|contains|matches|eval|excludes|soundslike" +
        "|memberof|not|in|or|and|exists|forall|over|from|entry|point|accumulate|acc|collect" +
        "|action|reverse|result|end|init|instanceof|extends|super|boolean|char|byte|short" +
        "|int|long|float|double|this|void|class|new|case|final|if|else|for|while|do" +
        "|default|try|catch|finally|switch|synchronized|return|throw|break|continue|assert" +
        "|modify|static|public|protected|private|abstract|native|transient|volatile" +
        "|strictfp|throws|interface|enum|implements|type|window|trait|no-loop|str");
    var langClasses = ("AbstractMethodError|AssertionError|ClassCircularityError|" +
        "ClassFormatError|Deprecated|EnumConstantNotPresentException|" +
        "ExceptionInInitializerError|IllegalAccessError|" +
        "IllegalThreadStateException|InstantiationError|InternalError|" +
        "NegativeArraySizeException|NoSuchFieldError|Override|Process|" +
        "ProcessBuilder|SecurityManager|StringIndexOutOfBoundsException|" +
        "SuppressWarnings|TypeNotPresentException|UnknownError|" +
        "UnsatisfiedLinkError|UnsupportedClassVersionError|VerifyError|" +
        "InstantiationException|IndexOutOfBoundsException|" +
        "ArrayIndexOutOfBoundsException|CloneNotSupportedException|" +
        "NoSuchFieldException|IllegalArgumentException|NumberFormatException|" +
        "SecurityException|Void|InheritableThreadLocal|IllegalStateException|" +
        "InterruptedException|NoSuchMethodException|IllegalAccessException|" +
        "UnsupportedOperationException|Enum|StrictMath|Package|Compiler|" +
        "Readable|Runtime|StringBuilder|Math|IncompatibleClassChangeError|" +
        "NoSuchMethodError|ThreadLocal|RuntimePermission|ArithmeticException|" +
        "NullPointerException|Long|Integer|Short|Byte|Double|Number|Float|" +
        "Character|Boolean|StackTraceElement|Appendable|StringBuffer|" +
        "Iterable|ThreadGroup|Runnable|Thread|IllegalMonitorStateException|" +
        "StackOverflowError|OutOfMemoryError|VirtualMachineError|" +
        "ArrayStoreException|ClassCastException|LinkageError|" +
        "NoClassDefFoundError|ClassNotFoundException|RuntimeException|" +
        "Exception|ThreadDeath|Error|Throwable|System|ClassLoader|" +
        "Cloneable|Class|CharSequence|Comparable|String|Object");
    var keywordMapper = this.createKeywordMapper({
        "variable.language": "this",
        "keyword": keywords,
        "constant.language": "null",
        "support.class": langClasses,
        "support.function": "retract|update|modify|insert"
    }, "identifier");
    var stringRules = function () {
        return [{
                token: "string", // single line
                regex: '["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]'
            }, {
                token: "string", // single line
                regex: "['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']"
            }];
    };
    var basicPreRules = function (blockCommentRules) {
        return [{
                token: "comment",
                regex: "\\/\\/.*$"
            },
            DocCommentHighlightRules.getStartRule("doc-start"),
            {
                token: "comment", // multi line comment
                regex: "\\/\\*",
                next: blockCommentRules
            }, {
                token: "constant.numeric", // hex
                regex: "0[xX][0-9a-fA-F]+\\b"
            }, {
                token: "constant.numeric", // float
                regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
            }, {
                token: "constant.language.boolean",
                regex: "(?:true|false)\\b"
            }];
    };
    var blockCommentRules = function (returnRule) {
        return [
            {
                token: "comment.block", // closing comment
                regex: "\\*\\/",
                next: returnRule
            }, {
                defaultToken: "comment.block"
            }
        ];
    };
    var basicPostRules = function () {
        return [{
                token: keywordMapper,
                regex: "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
            }, {
                token: "keyword.operator",
                regex: "!|\\$|%|&|\\*|\\-\\-|\\-|\\+\\+|\\+|~|===|==|=|!=|!==|<=|>=|<<=|>>=|>>>=|<>|<|>|!|&&|\\|\\||\\?\\:|\\*=|%=|\\+=|\\-=|&=|\\^=|\\b(?:in|instanceof|new|delete|typeof|void)"
            }, {
                token: "lparen",
                regex: "[[({]"
            }, {
                token: "rparen",
                regex: "[\\])}]"
            }, {
                token: "text",
                regex: "\\s+"
            }];
    };
    this.$rules = {
        "start": [].concat(basicPreRules("block.comment"), [
            {
                token: "entity.name.type",
                regex: "@[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
            }, {
                token: ["keyword", "text", "entity.name.type"],
                regex: "(package)(\\s+)(" + packageIdentifierRe + ")"
            }, {
                token: ["keyword", "text", "keyword", "text", "entity.name.type"],
                regex: "(import)(\\s+)(function)(\\s+)(" + packageIdentifierRe + ")"
            }, {
                token: ["keyword", "text", "entity.name.type"],
                regex: "(import)(\\s+)(" + packageIdentifierRe + ")"
            }, {
                token: ["keyword", "text", "entity.name.type", "text", "variable"],
                regex: "(global)(\\s+)(" + packageIdentifierRe + ")(\\s+)(" + identifierRe + ")"
            }, {
                token: ["keyword", "text", "keyword", "text", "entity.name.type"],
                regex: "(declare)(\\s+)(trait)(\\s+)(" + identifierRe + ")"
            }, {
                token: ["keyword", "text", "entity.name.type"],
                regex: "(declare)(\\s+)(" + identifierRe + ")"
            }, {
                token: ["keyword", "text", "entity.name.type"],
                regex: "(extends)(\\s+)(" + packageIdentifierRe + ")"
            }, {
                token: ["keyword", "text"],
                regex: "(rule)(\\s+)",
                next: "asset.name"
            }
        ], stringRules(), [{
                token: ["variable.other", "text", "text"],
                regex: "(" + identifierRe + ")(\\s*)(:)"
            }, {
                token: ["keyword", "text"],
                regex: "(query)(\\s+)",
                next: "asset.name"
            }, {
                token: ["keyword", "text"],
                regex: "(when)(\\s*)"
            }, {
                token: ["keyword", "text"],
                regex: "(then)(\\s*)",
                next: "java-start"
            }, {
                token: "paren.lparen",
                regex: /[\[({]/
            }, {
                token: "paren.rparen",
                regex: /[\])}]/
            }], basicPostRules()),
        "block.comment": blockCommentRules("start"),
        "asset.name": [
            {
                token: "entity.name",
                regex: '["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]'
            }, {
                token: "entity.name",
                regex: "['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']"
            }, {
                token: "entity.name",
                regex: identifierRe
            }, {
                regex: "",
                token: "empty",
                next: "start"
            }
        ]
    };
    this.embedRules(DocCommentHighlightRules, "doc-", [DocCommentHighlightRules.getEndRule("start")]);
    this.embedRules(JavaHighlightRules, "java-", [
        {
            token: "support.function",
            regex: "\\b(insert|modify|retract|update)\\b"
        }, {
            token: "keyword",
            regex: "\\bend\\b",
            next: "start"
        }
    ]);
};
oop.inherits(DroolsHighlightRules, TextHighlightRules);
exports.DroolsHighlightRules = DroolsHighlightRules;

});

define("ace/mode/folding/drools",["require","exports","module","ace/lib/oop","ace/range","ace/mode/folding/fold_mode","ace/token_iterator"], function(require, exports, module){"use strict";
var oop = require("../../lib/oop");
var Range = require("../../range").Range;
var BaseFoldMode = require("./fold_mode").FoldMode;
var TokenIterator = require("../../token_iterator").TokenIterator;
var FoldMode = exports.FoldMode = function () { };
oop.inherits(FoldMode, BaseFoldMode);
(function () {
    this.foldingStartMarker = /\b(rule|declare|query|when|then)\b/;
    this.foldingStopMarker = /\bend\b/;
    this.importRegex = /^import /;
    this.globalRegex = /^global /;
    this.getBaseFoldWidget = this.getFoldWidget;
    this.getFoldWidget = function (session, foldStyle, row) {
        if (foldStyle === "markbegin") {
            var line = session.getLine(row);
            if (this.importRegex.test(line)) {
                if (row === 0 || !this.importRegex.test(session.getLine(row - 1)))
                    return "start";
            }
            if (this.globalRegex.test(line)) {
                if (row === 0 || !this.globalRegex.test(session.getLine(row - 1)))
                    return "start";
            }
        }
        return this.getBaseFoldWidget(session, foldStyle, row);
    };
    this.getFoldWidgetRange = function (session, foldStyle, row) {
        var line = session.getLine(row);
        var match = line.match(this.foldingStartMarker);
        if (match) {
            if (match[1]) {
                var position = { row: row, column: line.length };
                var iterator = new TokenIterator(session, position.row, position.column);
                var seek = "end";
                var token = iterator.getCurrentToken();
                if (token.value == "when") {
                    seek = "then";
                }
                while (token) {
                    if (token.value == seek) {
                        return Range.fromPoints(position, {
                            row: iterator.getCurrentTokenRow(),
                            column: iterator.getCurrentTokenColumn()
                        });
                    }
                    token = iterator.stepForward();
                }
            }
        }
        match = line.match(this.importRegex);
        if (match) {
            return getMatchedFoldRange(session, this.importRegex, match, row);
        }
        match = line.match(this.globalRegex);
        if (match) {
            return getMatchedFoldRange(session, this.globalRegex, match, row);
        }
    };
}).call(FoldMode.prototype);
function getMatchedFoldRange(session, regex, match, row) {
    var startColumn = match[0].length;
    var maxRow = session.getLength();
    var startRow = row;
    var endRow = row;
    while (++row < maxRow) {
        var line = session.getLine(row);
        if (line.match(/^\s*$/))
            continue;
        if (!line.match(regex))
            break;
        endRow = row;
    }
    if (endRow > startRow) {
        var endColumn = session.getLine(endRow).length;
        return new Range(startRow, startColumn, endRow, endColumn);
    }
}

});

define("ace/mode/drools",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/drools_highlight_rules","ace/mode/folding/drools"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var DroolsHighlightRules = require("./drools_highlight_rules").DroolsHighlightRules;
var DroolsFoldMode = require("./folding/drools").FoldMode;
var Mode = function () {
    this.HighlightRules = DroolsHighlightRules;
    this.foldingRules = new DroolsFoldMode();
    this.$behaviour = this.$defaultBehaviour;
};
oop.inherits(Mode, TextMode);
(function () {
    this.lineCommentStart = "//";
    this.$id = "ace/mode/drools";
    this.snippetFileId = "ace/snippets/drools";
}).call(Mode.prototype);
exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/drools"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            