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

define("ace/mode/c_cpp_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/doc_comment_highlight_rules","ace/mode/text_highlight_rules"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var DocCommentHighlightRules = require("./doc_comment_highlight_rules").DocCommentHighlightRules;
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var cFunctions = exports.cFunctions = "hypot|hypotf|hypotl|sscanf|system|snprintf|scanf|scalbn|scalbnf|scalbnl|scalbln|scalblnf|scalblnl|sin|sinh|sinhf|sinhl|sinf|sinl|signal|signbit|strstr|strspn|strncpy|strncat|strncmp|strcspn|strchr|strcoll|strcpy|strcat|strcmp|strtoimax|strtod|strtoul|strtoull|strtoumax|strtok|strtof|strtol|strtold|strtoll|strerror|strpbrk|strftime|strlen|strrchr|strxfrm|sprintf|setjmp|setvbuf|setlocale|setbuf|sqrt|sqrtf|sqrtl|swscanf|swprintf|srand|nearbyint|nearbyintf|nearbyintl|nexttoward|nexttowardf|nexttowardl|nextafter|nextafterf|nextafterl|nan|nanf|nanl|csin|csinh|csinhf|csinhl|csinf|csinl|csqrt|csqrtf|csqrtl|ccos|ccosh|ccoshf|ccosf|ccosl|cimag|cimagf|cimagl|ctime|ctan|ctanh|ctanhf|ctanhl|ctanf|ctanl|cos|cosh|coshf|coshl|cosf|cosl|conj|conjf|conjl|copysign|copysignf|copysignl|cpow|cpowf|cpowl|cproj|cprojf|cprojl|ceil|ceilf|ceill|cexp|cexpf|cexpl|clock|clog|clogf|clogl|clearerr|casin|casinh|casinhf|casinhl|casinf|casinl|cacos|cacosh|cacoshf|cacoshl|cacosf|cacosl|catan|catanh|catanhf|catanhl|catanf|catanl|calloc|carg|cargf|cargl|cabs|cabsf|cabsl|creal|crealf|creall|cbrt|cbrtf|cbrtl|time|toupper|tolower|tan|tanh|tanhf|tanhl|tanf|tanl|trunc|truncf|truncl|tgamma|tgammaf|tgammal|tmpnam|tmpfile|isspace|isnormal|isnan|iscntrl|isinf|isdigit|isunordered|isupper|ispunct|isprint|isfinite|iswspace|iswcntrl|iswctype|iswdigit|iswupper|iswpunct|iswprint|iswlower|iswalnum|iswalpha|iswgraph|iswxdigit|iswblank|islower|isless|islessequal|islessgreater|isalnum|isalpha|isgreater|isgreaterequal|isgraph|isxdigit|isblank|ilogb|ilogbf|ilogbl|imaxdiv|imaxabs|div|difftime|_Exit|ungetc|ungetwc|pow|powf|powl|puts|putc|putchar|putwc|putwchar|perror|printf|erf|erfc|erfcf|erfcl|erff|erfl|exit|exp|exp2|exp2f|exp2l|expf|expl|expm1|expm1f|expm1l|vsscanf|vsnprintf|vscanf|vsprintf|vswscanf|vswprintf|vprintf|vfscanf|vfprintf|vfwscanf|vfwprintf|vwscanf|vwprintf|va_start|va_copy|va_end|va_arg|qsort|fscanf|fsetpos|fseek|fclose|ftell|fopen|fdim|fdimf|fdiml|fpclassify|fputs|fputc|fputws|fputwc|fprintf|feholdexcept|fesetenv|fesetexceptflag|fesetround|feclearexcept|fetestexcept|feof|feupdateenv|feraiseexcept|ferror|fegetenv|fegetexceptflag|fegetround|fflush|fwscanf|fwide|fwprintf|fwrite|floor|floorf|floorl|fabs|fabsf|fabsl|fgets|fgetc|fgetpos|fgetws|fgetwc|freopen|free|fread|frexp|frexpf|frexpl|fmin|fminf|fminl|fmod|fmodf|fmodl|fma|fmaf|fmal|fmax|fmaxf|fmaxl|ldiv|ldexp|ldexpf|ldexpl|longjmp|localtime|localeconv|log|log1p|log1pf|log1pl|log10|log10f|log10l|log2|log2f|log2l|logf|logl|logb|logbf|logbl|labs|lldiv|llabs|llrint|llrintf|llrintl|llround|llroundf|llroundl|lrint|lrintf|lrintl|lround|lroundf|lroundl|lgamma|lgammaf|lgammal|wscanf|wcsstr|wcsspn|wcsncpy|wcsncat|wcsncmp|wcscspn|wcschr|wcscoll|wcscpy|wcscat|wcscmp|wcstoimax|wcstod|wcstoul|wcstoull|wcstoumax|wcstok|wcstof|wcstol|wcstold|wcstoll|wcstombs|wcspbrk|wcsftime|wcslen|wcsrchr|wcsrtombs|wcsxfrm|wctob|wctomb|wcrtomb|wprintf|wmemset|wmemchr|wmemcpy|wmemcmp|wmemmove|assert|asctime|asin|asinh|asinhf|asinhl|asinf|asinl|acos|acosh|acoshf|acoshl|acosf|acosl|atoi|atof|atol|atoll|atexit|atan|atanh|atanhf|atanhl|atan2|atan2f|atan2l|atanf|atanl|abs|abort|gets|getc|getchar|getenv|getwc|getwchar|gmtime|rint|rintf|rintl|round|roundf|roundl|rename|realloc|rewind|remove|remquo|remquof|remquol|remainder|remainderf|remainderl|rand|raise|bsearch|btowc|modf|modff|modfl|memset|memchr|memcpy|memcmp|memmove|mktime|malloc|mbsinit|mbstowcs|mbsrtowcs|mbtowc|mblen|mbrtowc|mbrlen";
var c_cppHighlightRules = function (extraKeywords) {
    var keywordControls = ("break|case|continue|default|do|else|for|goto|if|_Pragma|" +
        "return|switch|while|catch|operator|try|throw|using");
    var storageType = ("asm|__asm__|auto|bool|_Bool|char|_Complex|double|enum|float|" +
        "_Imaginary|int|int8_t|int16_t|int32_t|int64_t|long|short|signed|size_t|struct|typedef|uint8_t|uint16_t|uint32_t|uint64_t|union|unsigned|void|" +
        "class|wchar_t|template|char16_t|char32_t");
    var storageModifiers = ("const|extern|register|restrict|static|volatile|inline|private|" +
        "protected|public|friend|explicit|virtual|export|mutable|typename|" +
        "constexpr|new|delete|alignas|alignof|decltype|noexcept|thread_local");
    var keywordOperators = ("and|and_eq|bitand|bitor|compl|not|not_eq|or|or_eq|typeid|xor|xor_eq|" +
        "const_cast|dynamic_cast|reinterpret_cast|static_cast|sizeof|namespace");
    var builtinConstants = ("NULL|true|false|TRUE|FALSE|nullptr");
    var keywordMapper = this.$keywords = this.createKeywordMapper(Object.assign({
        "keyword.control": keywordControls,
        "storage.type": storageType,
        "storage.modifier": storageModifiers,
        "keyword.operator": keywordOperators,
        "variable.language": "this",
        "constant.language": builtinConstants,
        "support.function.C99.c": cFunctions
    }, extraKeywords), "identifier");
    var identifierRe = "[a-zA-Z\\$_\u00a1-\uffff][a-zA-Z\\d\\$_\u00a1-\uffff]*\\b";
    var escapeRe = /\\(?:['"?\\abfnrtv]|[0-7]{1,3}|x[a-fA-F\d]{2}|u[a-fA-F\d]{4}U[a-fA-F\d]{8}|.)/.source;
    var formatRe = "%"
        + /(\d+\$)?/.source // field (argument #)
        + /[#0\- +']*/.source // flags
        + /[,;:_]?/.source // separator character (AltiVec)
        + /((-?\d+)|\*(-?\d+\$)?)?/.source // minimum field width
        + /(\.((-?\d+)|\*(-?\d+\$)?)?)?/.source // precision
        + /(hh|h|ll|l|j|t|z|q|L|vh|vl|v|hv|hl)?/.source // length modifier
        + /(\[[^"\]]+\]|[diouxXDOUeEfFgGaACcSspn%])/.source; // conversion type
    this.$rules = {
        "start": [
            {
                token: "comment",
                regex: "//$",
                next: "start"
            }, {
                token: "comment",
                regex: "//",
                next: "singleLineComment"
            },
            DocCommentHighlightRules.getStartRule("doc-start"),
            {
                token: "comment", // multi line comment
                regex: "\\/\\*",
                next: "comment"
            }, {
                token: "string", // character
                regex: "'(?:" + escapeRe + "|.)?'"
            }, {
                token: "string.start",
                regex: '"',
                stateName: "qqstring",
                next: [
                    { token: "string", regex: /\\\s*$/, next: "qqstring" },
                    { token: "constant.language.escape", regex: escapeRe },
                    { token: "constant.language.escape", regex: formatRe },
                    { token: "string.end", regex: '"|$', next: "start" },
                    { defaultToken: "string" }
                ]
            }, {
                token: "string.start",
                regex: 'R"\\(',
                stateName: "rawString",
                next: [
                    { token: "string.end", regex: '\\)"', next: "start" },
                    { defaultToken: "string" }
                ]
            }, {
                token: "constant.numeric", // hex
                regex: "0[xX][0-9a-fA-F]+(L|l|UL|ul|u|U|F|f|ll|LL|ull|ULL)?\\b"
            }, {
                token: "constant.numeric", // float
                regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?(L|l|UL|ul|u|U|F|f|ll|LL|ull|ULL)?\\b"
            }, {
                token: "keyword", // pre-compiler directives
                regex: "#\\s*(?:include|import|pragma|line|define|undef)\\b",
                next: "directive"
            }, {
                token: "keyword", // special case pre-compiler directive
                regex: "#\\s*(?:endif|if|ifdef|else|elif|ifndef)\\b"
            }, {
                token: keywordMapper,
                regex: "[a-zA-Z_$][a-zA-Z0-9_$]*"
            }, {
                token: "keyword.operator",
                regex: /--|\+\+|<<=|>>=|>>>=|<>|&&|\|\||\?:|[*%\/+\-&\^|~!<>=]=?/
            }, {
                token: "punctuation.operator",
                regex: "\\?|\\:|\\,|\\;|\\."
            }, {
                token: "paren.lparen",
                regex: "[[({]"
            }, {
                token: "paren.rparen",
                regex: "[\\])}]"
            }, {
                token: "text",
                regex: "\\s+"
            }
        ],
        "comment": [
            {
                token: "comment", // closing comment
                regex: "\\*\\/",
                next: "start"
            }, {
                defaultToken: "comment"
            }
        ],
        "singleLineComment": [
            {
                token: "comment",
                regex: /\\$/,
                next: "singleLineComment"
            }, {
                token: "comment",
                regex: /$/,
                next: "start"
            }, {
                defaultToken: "comment"
            }
        ],
        "directive": [
            {
                token: "constant.other.multiline",
                regex: /\\/
            },
            {
                token: "constant.other.multiline",
                regex: /.*\\/
            },
            {
                token: "constant.other",
                regex: "\\s*<.+?>",
                next: "start"
            },
            {
                token: "constant.other", // single line
                regex: '\\s*["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]',
                next: "start"
            },
            {
                token: "constant.other", // single line
                regex: "\\s*['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']",
                next: "start"
            },
            {
                token: "constant.other",
                regex: /[^\\\/]+/,
                next: "start"
            }
        ]
    };
    this.embedRules(DocCommentHighlightRules, "doc-", [DocCommentHighlightRules.getEndRule("start")]);
    this.normalizeRules();
};
oop.inherits(c_cppHighlightRules, TextHighlightRules);
exports.c_cppHighlightRules = c_cppHighlightRules;

});

define("ace/mode/matching_brace_outdent",["require","exports","module","ace/range"], function(require, exports, module){"use strict";
var Range = require("../range").Range;
var MatchingBraceOutdent = function () { };
(function () {
    this.checkOutdent = function (line, input) {
        if (!/^\s+$/.test(line))
            return false;
        return /^\s*\}/.test(input);
    };
    this.autoOutdent = function (doc, row) {
        var line = doc.getLine(row);
        var match = line.match(/^(\s*\})/);
        if (!match)
            return 0;
        var column = match[1].length;
        var openBracePos = doc.findMatchingBracket({ row: row, column: column });
        if (!openBracePos || openBracePos.row == row)
            return 0;
        var indent = this.$getIndent(doc.getLine(openBracePos.row));
        doc.replace(new Range(row, 0, row, column - 1), indent);
    };
    this.$getIndent = function (line) {
        return line.match(/^\s*/)[0];
    };
}).call(MatchingBraceOutdent.prototype);
exports.MatchingBraceOutdent = MatchingBraceOutdent;

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

define("ace/mode/c_cpp",["require","exports","module","ace/lib/oop","ace/mode/text","ace/mode/c_cpp_highlight_rules","ace/mode/matching_brace_outdent","ace/mode/folding/cstyle"], function(require, exports, module){"use strict";
var oop = require("../lib/oop");
var TextMode = require("./text").Mode;
var c_cppHighlightRules = require("./c_cpp_highlight_rules").c_cppHighlightRules;
var MatchingBraceOutdent = require("./matching_brace_outdent").MatchingBraceOutdent;
var CStyleFoldMode = require("./folding/cstyle").FoldMode;
var Mode = function () {
    this.HighlightRules = c_cppHighlightRules;
    this.$outdent = new MatchingBraceOutdent();
    this.$behaviour = this.$defaultBehaviour;
    this.foldingRules = new CStyleFoldMode();
};
oop.inherits(Mode, TextMode);
(function () {
    this.lineCommentStart = "//";
    this.blockComment = { start: "/*", end: "*/" };
    this.getNextLineIndent = function (state, line, tab) {
        var indent = this.$getIndent(line);
        var tokenizedLine = this.getTokenizer().getLineTokens(line, state);
        var tokens = tokenizedLine.tokens;
        var endState = tokenizedLine.state;
        if (tokens.length && tokens[tokens.length - 1].type == "comment") {
            return indent;
        }
        if (state == "start") {
            var match = line.match(/^.*[\{\(\[]\s*$/);
            if (match) {
                indent += tab;
            }
        }
        else if (state == "doc-start") {
            if (endState == "start") {
                return "";
            }
            var match = line.match(/^\s*(\/?)\*/);
            if (match) {
                if (match[1]) {
                    indent += " ";
                }
                indent += "* ";
            }
        }
        return indent;
    };
    this.checkOutdent = function (state, line, input) {
        return this.$outdent.checkOutdent(line, input);
    };
    this.autoOutdent = function (state, doc, row) {
        this.$outdent.autoOutdent(doc, row);
    };
    this.$id = "ace/mode/c_cpp";
    this.snippetFileId = "ace/snippets/c_cpp";
}).call(Mode.prototype);
exports.Mode = Mode;

});

define("ace/mode/dart_highlight_rules",["require","exports","module","ace/lib/oop","ace/mode/doc_comment_highlight_rules","ace/mode/text_highlight_rules"], function(require, exports, module){/*
  THIS FILE WAS AUTOGENERATED BY mode_highlight_rules.tmpl.js (UUID: 958518BC-799F-477A-99F9-5B28EBF230F6) */
"use strict";
var oop = require("../lib/oop");
var DocCommentHighlightRules = require("./doc_comment_highlight_rules").DocCommentHighlightRules;
var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
var DartHighlightRules = function () {
    var constantLanguage = "true|false|null";
    var variableLanguage = "this|super";
    var keywordControl = "try|catch|finally|throw|rethrow|assert|break|case|continue|default|do|else|for|if|in|return|switch|while|new|deferred|async|await";
    var keywordDeclaration = "abstract|class|extends|external|factory|implements|get|native|operator|set|typedef|with|enum";
    var storageModifier = "static|final|const";
    var storageType = "void|bool|num|int|double|dynamic|var|String";
    var keywordMapper = this.createKeywordMapper({
        "constant.language.dart": constantLanguage,
        "variable.language.dart": variableLanguage,
        "keyword.control.dart": keywordControl,
        "keyword.declaration.dart": keywordDeclaration,
        "storage.modifier.dart": storageModifier,
        "storage.type.primitive.dart": storageType
    }, "identifier");
    var stringfill = [{
            token: "constant.language.escape",
            regex: /\\./
        }, {
            token: "text",
            regex: /\$(?:\w+|{[^"'}]+})?/
        }, {
            defaultToken: "string"
        }];
    this.$rules = {
        "start": [
            {
                token: "comment",
                regex: /\/\/.*$/
            },
            DocCommentHighlightRules.getStartRule("doc-start"),
            {
                token: "comment", // multi line comment
                regex: /\/\*/,
                next: "comment"
            },
            {
                token: ["meta.preprocessor.script.dart"],
                regex: "^(#!.*)$"
            },
            {
                token: "keyword.other.import.dart",
                regex: "(?:\\b)(?:library|import|export|part|of|show|hide)(?:\\b)"
            },
            {
                token: ["keyword.other.import.dart", "text"],
                regex: "(?:\\b)(prefix)(\\s*:)"
            },
            {
                regex: "\\bas\\b",
                token: "keyword.cast.dart"
            },
            {
                regex: "\\?|:",
                token: "keyword.control.ternary.dart"
            },
            {
                regex: "(?:\\b)(is\\!?)(?:\\b)",
                token: ["keyword.operator.dart"]
            },
            {
                regex: "(<<|>>>?|~|\\^|\\||&)",
                token: ["keyword.operator.bitwise.dart"]
            },
            {
                regex: "((?:&|\\^|\\||<<|>>>?)=)",
                token: ["keyword.operator.assignment.bitwise.dart"]
            },
            {
                regex: "(===?|!==?|<=?|>=?)",
                token: ["keyword.operator.comparison.dart"]
            },
            {
                regex: "((?:[+*/%-]|\\~)=)",
                token: ["keyword.operator.assignment.arithmetic.dart"]
            },
            {
                regex: "=",
                token: "keyword.operator.assignment.dart"
            },
            {
                token: "string",
                regex: "'''",
                next: "qdoc"
            },
            {
                token: "string",
                regex: '"""',
                next: "qqdoc"
            },
            {
                token: "string",
                regex: "'",
                next: "qstring"
            },
            {
                token: "string",
                regex: '"',
                next: "qqstring"
            },
            {
                regex: "(\\-\\-|\\+\\+)",
                token: ["keyword.operator.increment-decrement.dart"]
            },
            {
                regex: "(\\-|\\+|\\*|\\/|\\~\\/|%)",
                token: ["keyword.operator.arithmetic.dart"]
            },
            {
                regex: "(!|&&|\\|\\|)",
                token: ["keyword.operator.logical.dart"]
            },
            {
                token: "constant.numeric", // hex
                regex: "0[xX][0-9a-fA-F]+\\b"
            },
            {
                token: "constant.numeric", // float
                regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
            },
            {
                token: keywordMapper,
                regex: "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
            }
        ],
        "comment": [
            {
                token: "comment", // closing comment
                regex: "\\*\\/",
                next: "start"
            }, {
                defaultToken: "comment"
            }
        ],
        "qdoc": [
            {
                token: "string",
                regex: "'''",
                next: "start"
            }
        ].concat(stringfill),
        "qqdoc": [
            {
                token: "string",
                regex: '"""',
                next: "start"
            }
        ].concat(stringfill),
        "qstring": [
            {
                token: "string",
                regex: "'|$",
                next: "start"
            }
        ].concat(stringfill),
        "qqstring": [
            {
                token: "string",
                regex: '"|$',
                next: "start"
            }
        ].concat(stringfill)
    };
    this.embedRules(DocCommentHighlightRules, "doc-", [DocCommentHighlightRules.getEndRule("start")]);
};
oop.inherits(DartHighlightRules, TextHighlightRules);
exports.DartHighlightRules = DartHighlightRules;

});

define("ace/mode/dart",["require","exports","module","ace/lib/oop","ace/mode/c_cpp","ace/mode/dart_highlight_rules","ace/mode/folding/cstyle"], function(require, exports, module){/*
  THIS FILE WAS AUTOGENERATED BY mode.tmpl.js
*/
"use strict";
var oop = require("../lib/oop");
var CMode = require("./c_cpp").Mode;
var DartHighlightRules = require("./dart_highlight_rules").DartHighlightRules;
var CStyleFoldMode = require("./folding/cstyle").FoldMode;
var Mode = function () {
    CMode.call(this);
    this.HighlightRules = DartHighlightRules;
    this.foldingRules = new CStyleFoldMode();
    this.$behaviour = this.$defaultBehaviour;
};
oop.inherits(Mode, CMode);
(function () {
    this.lineCommentStart = "//";
    this.blockComment = { start: "/*", end: "*/" };
    this.$id = "ace/mode/dart";
    this.snippetFileId = "ace/snippets/dart";
}).call(Mode.prototype);
exports.Mode = Mode;

});                (function() {
                    window.require(["ace/mode/dart"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            