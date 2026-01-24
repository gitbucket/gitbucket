define("ace/ext/diff/scroll_diff_decorator",["require","exports","module","ace/layer/decorators"], function(require, exports, module){var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (Object.prototype.hasOwnProperty.call(b, p)) d[p] = b[p]; };
        return extendStatics(d, b);
    };
    return function (d, b) {
        if (typeof b !== "function" && b !== null)
            throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
var __values = (this && this.__values) || function(o) {
    var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
    if (m) return m.call(o);
    if (o && typeof o.length === "number") return {
        next: function () {
            if (o && i >= o.length) o = void 0;
            return { value: o && o[i++], done: !o };
        }
    };
    throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
};
var Decorator = require("../../layer/decorators").Decorator;
var ScrollDiffDecorator = /** @class */ (function (_super) {
    __extends(ScrollDiffDecorator, _super);
    function ScrollDiffDecorator(scrollbarV, renderer, forInlineDiff) {
        var _this = _super.call(this, scrollbarV, renderer) || this;
        _this.colors.dark["delete"] = "rgba(255, 18, 18, 1)";
        _this.colors.dark["insert"] = "rgba(18, 136, 18, 1)";
        _this.colors.light["delete"] = "rgb(255,51,51)";
        _this.colors.light["insert"] = "rgb(32,133,72)";
        _this.$zones = [];
        _this.$forInlineDiff = forInlineDiff;
        return _this;
    }
    ScrollDiffDecorator.prototype.addZone = function (startRow, endRow, type) {
        this.$zones.push({
            startRow: startRow,
            endRow: endRow,
            type: type
        });
    };
    ScrollDiffDecorator.prototype.setSessions = function (sessionA, sessionB) {
        this.sessionA = sessionA;
        this.sessionB = sessionB;
    };
    ScrollDiffDecorator.prototype.$updateDecorators = function (config) {
        if (typeof this.canvas.getContext !== "function") {
            return;
        }
        _super.prototype.$updateDecorators.call(this, config);
        if (this.$zones.length > 0) {
            var colors = (this.renderer.theme.isDark === true) ? this.colors.dark : this.colors.light;
            var ctx = this.canvas.getContext("2d");
            this.$setDiffDecorators(ctx, colors);
        }
    };
    ScrollDiffDecorator.prototype.$transformPosition = function (row, type) {
        if (type == "delete") {
            return this.sessionA.documentToScreenRow(row, 0);
        }
        else {
            return this.sessionB.documentToScreenRow(row, 0);
        }
    };
    ScrollDiffDecorator.prototype.$setDiffDecorators = function (ctx, colors) {
        var e_1, _a;
        var _this = this;
        function compare(a, b) {
            if (a.from === b.from) {
                return a.to - b.to;
            }
            return a.from - b.from;
        }
        var zones = this.$zones;
        if (zones) {
            var resolvedZones = [];
            var deleteZones = zones.filter(function (z) { return z.type === "delete"; });
            var insertZones = zones.filter(function (z) { return z.type === "insert"; });
            [deleteZones, insertZones].forEach(function (typeZones) {
                typeZones.forEach(function (zone, i) {
                    var offset1 = _this.$transformPosition(zone.startRow, zone.type) * _this.lineHeight;
                    var offset2 = _this.$transformPosition(zone.endRow, zone.type) * _this.lineHeight + _this.lineHeight;
                    var y1 = Math.round(_this.heightRatio * offset1);
                    var y2 = Math.round(_this.heightRatio * offset2);
                    var padding = 1;
                    var ycenter = Math.round((y1 + y2) / 2);
                    var halfHeight = (y2 - ycenter);
                    if (halfHeight < _this.halfMinDecorationHeight) {
                        halfHeight = _this.halfMinDecorationHeight;
                    }
                    var previousZone = resolvedZones[resolvedZones.length - 1];
                    if (i > 0 && previousZone && previousZone.type === zone.type && ycenter - halfHeight < previousZone.to + padding) {
                        ycenter = resolvedZones[resolvedZones.length - 1].to + padding + halfHeight;
                    }
                    if (ycenter - halfHeight < 0) {
                        ycenter = halfHeight;
                    }
                    if (ycenter + halfHeight > _this.canvasHeight) {
                        ycenter = _this.canvasHeight - halfHeight;
                    }
                    resolvedZones.push({
                        type: zone.type,
                        from: ycenter - halfHeight,
                        to: ycenter + halfHeight,
                        color: colors[zone.type] || null
                    });
                });
            });
            resolvedZones = resolvedZones.sort(compare);
            try {
                for (var resolvedZones_1 = __values(resolvedZones), resolvedZones_1_1 = resolvedZones_1.next(); !resolvedZones_1_1.done; resolvedZones_1_1 = resolvedZones_1.next()) {
                    var zone = resolvedZones_1_1.value;
                    ctx.fillStyle = zone.color || null;
                    var zoneFrom = zone.from;
                    var zoneTo = zone.to;
                    var zoneHeight = zoneTo - zoneFrom;
                    if (this.$forInlineDiff) {
                        ctx.fillRect(this.oneZoneWidth, zoneFrom, 2 * this.oneZoneWidth, zoneHeight);
                    }
                    else {
                        if (zone.type == "delete") {
                            ctx.fillRect(this.oneZoneWidth, zoneFrom, this.oneZoneWidth, zoneHeight);
                        }
                        else {
                            ctx.fillRect(2 * this.oneZoneWidth, zoneFrom, this.oneZoneWidth, zoneHeight);
                        }
                    }
                }
            }
            catch (e_1_1) { e_1 = { error: e_1_1 }; }
            finally {
                try {
                    if (resolvedZones_1_1 && !resolvedZones_1_1.done && (_a = resolvedZones_1.return)) _a.call(resolvedZones_1);
                }
                finally { if (e_1) throw e_1.error; }
            }
        }
    };
    ScrollDiffDecorator.prototype.setZoneWidth = function () {
        this.oneZoneWidth = Math.round(this.canvasWidth / 3);
    };
    return ScrollDiffDecorator;
}(Decorator));
exports.ScrollDiffDecorator = ScrollDiffDecorator;

});

define("ace/ext/diff/styles-css.js",["require","exports","module"], function(require, exports, module){exports.cssText = "\n/*\n * Line Markers\n */\n.ace_diff {\n    position: absolute;\n    z-index: 0;\n}\n.ace_diff.inline {\n    z-index: 20;\n}\n/*\n * Light Colors \n */\n.ace_diff.insert {\n    background-color: #EFFFF1;\n}\n.ace_diff.delete {\n    background-color: #FFF1F1;\n}\n.ace_diff.aligned_diff {\n    background: rgba(206, 194, 191, 0.26);\n    background: repeating-linear-gradient(\n                45deg,\n              rgba(122, 111, 108, 0.26),\n              rgba(122, 111, 108, 0.26) 5px,\n              rgba(0, 0, 0, 0) 5px,\n              rgba(0, 0, 0, 0) 10px \n    );\n}\n\n.ace_diff.insert.inline {\n    background-color:  rgb(74 251 74 / 18%); \n}\n.ace_diff.delete.inline {\n    background-color: rgb(251 74 74 / 15%);\n}\n\n.ace_diff.delete.inline.empty {\n    background-color: rgba(255, 128, 79, 0.7);\n    width: 2px !important;\n}\n\n.ace_diff.insert.inline.empty {\n    background-color: rgba(49, 230, 96, 0.7);\n    width: 2px !important;\n}\n\n.ace_diff-active-line {\n    border-bottom: 1px solid;\n    border-top: 1px solid;\n    background: transparent;\n    position: absolute;\n    box-sizing: border-box;\n    border-color: #9191ac;\n}\n\n.ace_dark .ace_diff-active-line {\n    background: transparent;\n    border-color: #75777a;\n}\n \n\n/* gutter changes */\n.ace_mini-diff_gutter-enabled > .ace_gutter-cell,\n.ace_mini-diff_gutter-enabled > .ace_gutter-cell_svg-icons {\n    padding-right: 13px;\n}\n\n.ace_mini-diff_gutter_other > .ace_gutter-cell,\n.ace_mini-diff_gutter_other > .ace_gutter-cell_svg-icons  {\n    display: none;\n}\n\n.ace_mini-diff_gutter_other {\n    pointer-events: none;\n}\n\n\n.ace_mini-diff_gutter-enabled > .mini-diff-added {\n    background-color: #EFFFF1;\n    border-left: 3px solid #2BB534;\n    padding-left: 16px;\n    display: block;\n}\n\n.ace_mini-diff_gutter-enabled > .mini-diff-deleted {\n    background-color: #FFF1F1;\n    border-left: 3px solid #EA7158;\n    padding-left: 16px;\n    display: block;\n}\n\n\n.ace_mini-diff_gutter-enabled > .mini-diff-added:after {\n    position: absolute;\n    right: 2px;\n    content: \"+\";\n    background-color: inherit;\n}\n\n.ace_mini-diff_gutter-enabled > .mini-diff-deleted:after {\n    position: absolute;\n    right: 2px;\n    content: \"-\";\n    background-color: inherit;\n}\n.ace_fade-fold-widgets:hover > .ace_folding-enabled > .mini-diff-added:after,\n.ace_fade-fold-widgets:hover > .ace_folding-enabled > .mini-diff-deleted:after {\n    display: none;\n}\n\n.ace_diff_other .ace_selection {\n    filter: drop-shadow(1px 2px 3px darkgray);\n}\n\n.ace_hidden_marker-layer .ace_bracket,\n.ace_hidden_marker-layer .ace_error_bracket {\n    display: none;\n}\n\n\n\n/*\n * Dark Colors \n */\n\n.ace_dark .ace_diff.insert {\n    background-color: #212E25;\n}\n.ace_dark .ace_diff.delete {\n    background-color: #3F2222;\n}\n\n.ace_dark .ace_mini-diff_gutter-enabled > .mini-diff-added {\n    background-color: #212E25;\n    border-left-color:#00802F;\n}\n\n.ace_dark .ace_mini-diff_gutter-enabled > .mini-diff-deleted {\n    background-color: #3F2222;\n    border-left-color: #9C3838;\n}\n\n";

});

define("ace/ext/diff/gutter_decorator",["require","exports","module","ace/lib/dom"], function(require, exports, module){var dom = require("../../lib/dom");
var MinimalGutterDiffDecorator = /** @class */ (function () {
    function MinimalGutterDiffDecorator(editor, type) {
        this.gutterClass = "ace_mini-diff_gutter-enabled";
        this.gutterCellsClasses = {
            add: "mini-diff-added",
            delete: "mini-diff-deleted",
        };
        this.editor = editor;
        this.type = type;
        this.chunks = [];
        this.attachToEditor();
    }
    MinimalGutterDiffDecorator.prototype.attachToEditor = function () {
        this.renderGutters = this.renderGutters.bind(this);
        dom.addCssClass(this.editor.renderer.$gutterLayer.element, this.gutterClass);
        this.editor.renderer.$gutterLayer.on("afterRender", this.renderGutters);
    };
    MinimalGutterDiffDecorator.prototype.renderGutters = function (e, gutterLayer) {
        var _this = this;
        var cells = this.editor.renderer.$gutterLayer.$lines.cells;
        cells.forEach(function (cell) {
            cell.element.classList.remove(Object.values(_this.gutterCellsClasses));
        });
        var dir = this.type === -1 ? "old" : "new";
        var diffClass = this.type === -1 ? this.gutterCellsClasses.delete : this.gutterCellsClasses.add;
        this.chunks.forEach(function (lineChange) {
            var startRow = lineChange[dir].start.row;
            var endRow = lineChange[dir].end.row - 1;
            cells.forEach(function (cell) {
                if (cell.row >= startRow && cell.row <= endRow) {
                    cell.element.classList.add(diffClass);
                }
            });
        });
    };
    MinimalGutterDiffDecorator.prototype.setDecorations = function (changes) {
        this.chunks = changes;
        this.renderGutters();
    };
    MinimalGutterDiffDecorator.prototype.dispose = function () {
        dom.removeCssClass(this.editor.renderer.$gutterLayer.element, this.gutterClass);
        this.editor.renderer.$gutterLayer.off("afterRender", this.renderGutters);
    };
    return MinimalGutterDiffDecorator;
}());
exports.MinimalGutterDiffDecorator = MinimalGutterDiffDecorator;

});

define("ace/ext/diff/base_diff_view",["require","exports","module","ace/lib/oop","ace/range","ace/lib/dom","ace/config","ace/line_widgets","ace/ext/diff/scroll_diff_decorator","ace/ext/diff/styles-css.js","ace/editor","ace/virtual_renderer","ace/undomanager","ace/layer/decorators","ace/theme/textmate","ace/multi_select","ace/edit_session","ace/ext/diff/gutter_decorator"], function(require, exports, module){"use strict";
var __read = (this && this.__read) || function (o, n) {
    var m = typeof Symbol === "function" && o[Symbol.iterator];
    if (!m) return o;
    var i = m.call(o), r, ar = [], e;
    try {
        while ((n === void 0 || n-- > 0) && !(r = i.next()).done) ar.push(r.value);
    }
    catch (error) { e = { error: error }; }
    finally {
        try {
            if (r && !r.done && (m = i["return"])) m.call(i);
        }
        finally { if (e) throw e.error; }
    }
    return ar;
};
var oop = require("../../lib/oop");
var Range = require("../../range").Range;
var dom = require("../../lib/dom");
var config = require("../../config");
var LineWidgets = require("../../line_widgets").LineWidgets;
var ScrollDiffDecorator = require("./scroll_diff_decorator").ScrollDiffDecorator;
var css = require("./styles-css.js").cssText;
var Editor = require("../../editor").Editor;
var Renderer = require("../../virtual_renderer").VirtualRenderer;
var UndoManager = require("../../undomanager").UndoManager;
var Decorator = require("../../layer/decorators").Decorator;
require("../../theme/textmate");
require("../../multi_select");
var EditSession = require("../../edit_session").EditSession;
var MinimalGutterDiffDecorator = require("./gutter_decorator").MinimalGutterDiffDecorator;
var dummyDiffProvider = {
    compute: function (val1, val2, options) {
        return [];
    }
};
dom.importCssString(css, "diffview.css");
var BaseDiffView = /** @class */ (function () {
    function BaseDiffView(inlineDiffEditor, container) {
        this.onChangeTheme = this.onChangeTheme.bind(this);
        this.onInput = this.onInput.bind(this);
        this.onChangeFold = this.onChangeFold.bind(this);
        this.realign = this.realign.bind(this);
        this.onSelect = this.onSelect.bind(this);
        this.onChangeWrapLimit = this.onChangeWrapLimit.bind(this);
        this.realignPending = false; this.diffSession; this.chunks;
        this.inlineDiffEditor = inlineDiffEditor || false;
        this.currentDiffIndex = 0;
        this.diffProvider = dummyDiffProvider;
        if (container) {
            this.container = container;
        }
        this.$ignoreTrimWhitespace = false;
        this.$maxDiffs = 5000;
        this.$maxComputationTimeMs = 150;
        this.$syncSelections = false;
        this.$foldUnchangedOnInput = false;
        this.markerB = new DiffHighlight(this, 1);
        this.markerA = new DiffHighlight(this, -1);
    }
    BaseDiffView.prototype.$setupModels = function (diffModel) {
        if (diffModel.diffProvider) {
            this.setProvider(diffModel.diffProvider);
        }
        this.showSideA = diffModel.inline == undefined ? true : diffModel.inline === "a";
        var diffEditorOptions = /**@type {Partial<import("../../../ace-internal").Ace.EditorOptions>}*/ ({
            scrollPastEnd: 0.5,
            highlightActiveLine: false,
            highlightGutterLine: false,
            animatedScroll: true,
            customScrollbar: true,
            vScrollBarAlwaysVisible: true,
            fadeFoldWidgets: true,
            showFoldWidgets: true,
            selectionStyle: "text",
        });
        this.savedOptionsA = diffModel.editorA && diffModel.editorA.getOptions(diffEditorOptions);
        this.savedOptionsB = diffModel.editorB && diffModel.editorB.getOptions(diffEditorOptions);
        if (!this.inlineDiffEditor || diffModel.inline === "a") {
            this.editorA = diffModel.editorA || this.$setupModel(diffModel.sessionA, diffModel.valueA);
            this.container && this.container.appendChild(this.editorA.container);
            this.editorA.setOptions(diffEditorOptions);
        }
        if (!this.inlineDiffEditor || diffModel.inline === "b") {
            this.editorB = diffModel.editorB || this.$setupModel(diffModel.sessionB, diffModel.valueB);
            this.container && this.container.appendChild(this.editorB.container);
            this.editorB.setOptions(diffEditorOptions);
        }
        if (this.inlineDiffEditor) {
            this.activeEditor = this.showSideA ? this.editorA : this.editorB;
            this.otherSession = this.showSideA ? this.sessionB : this.sessionA;
            var cloneOptions = this.activeEditor.getOptions();
            cloneOptions.readOnly = true;
            delete cloneOptions.mode;
            this.otherEditor = new Editor(new Renderer(null), undefined, cloneOptions);
            if (this.showSideA) {
                this.editorB = this.otherEditor;
            }
            else {
                this.editorA = this.otherEditor;
            }
        }
        this.setDiffSession({
            sessionA: diffModel.sessionA || (diffModel.editorA ? diffModel.editorA.session : new EditSession(diffModel.valueA || "")),
            sessionB: diffModel.sessionB || (diffModel.editorB ? diffModel.editorB.session : new EditSession(diffModel.valueB || "")),
            chunks: []
        });
        if (this.otherEditor && this.activeEditor) {
            this.otherSession.setOption("wrap", this.activeEditor.getOption("wrap"));
        }
        this.setupScrollbars();
    };
    BaseDiffView.prototype.addGutterDecorators = function () {
        if (!this.gutterDecoratorA)
            this.gutterDecoratorA = new MinimalGutterDiffDecorator(this.editorA, -1);
        if (!this.gutterDecoratorB)
            this.gutterDecoratorB = new MinimalGutterDiffDecorator(this.editorB, 1);
    };
    BaseDiffView.prototype.$setupModel = function (session, value) {
        var editor = new Editor(new Renderer(), session);
        editor.session.setUndoManager(new UndoManager());
        if (value != undefined) {
            editor.setValue(value, -1);
        }
        return editor;
    };
    BaseDiffView.prototype.foldUnchanged = function () {
        var chunks = this.chunks;
        var placeholder = "-".repeat(120);
        var prev = {
            old: new Range(0, 0, 0, 0),
            new: new Range(0, 0, 0, 0)
        };
        var foldsChanged = false;
        for (var i = 0; i < chunks.length + 1; i++) {
            var current = chunks[i] || {
                old: new Range(this.sessionA.getLength(), 0, this.sessionA.getLength(), 0),
                new: new Range(this.sessionB.getLength(), 0, this.sessionB.getLength(), 0)
            };
            var l = current.new.start.row - prev.new.end.row - 5;
            if (l > 2) {
                var s = prev.old.end.row + 2;
                var fold1 = this.sessionA.addFold(placeholder, new Range(s, 0, s + l, Number.MAX_VALUE));
                s = prev.new.end.row + 2;
                var fold2 = this.sessionB.addFold(placeholder, new Range(s, 0, s + l, Number.MAX_VALUE));
                if (fold1 || fold2)
                    foldsChanged = true;
                if (fold2 && fold1) {
                    fold1["other"] = fold2;
                    fold2["other"] = fold1;
                }
            }
            prev = current;
        }
        return foldsChanged;
    };
    BaseDiffView.prototype.unfoldUnchanged = function () {
        var folds = this.sessionA.getAllFolds();
        for (var i = folds.length - 1; i >= 0; i--) {
            var fold = folds[i];
            if (fold.placeholder.length == 120) {
                this.sessionA.removeFold(fold);
            }
        }
    };
    BaseDiffView.prototype.toggleFoldUnchanged = function () {
        if (!this.foldUnchanged()) {
            this.unfoldUnchanged();
        }
    };
    BaseDiffView.prototype.setDiffSession = function (session) {
        if (this.diffSession) {
            this.$detachSessionsEventHandlers();
            this.clearSelectionMarkers();
        }
        this.diffSession = session;
        this.sessionA = this.sessionB = null;
        if (this.diffSession) {
            this.chunks = this.diffSession.chunks || [];
            this.editorA && this.editorA.setSession(session.sessionA);
            this.editorB && this.editorB.setSession(session.sessionB);
            this.sessionA = this.diffSession.sessionA;
            this.sessionB = this.diffSession.sessionB;
            this.$attachSessionsEventHandlers();
            this.initSelectionMarkers();
        }
        this.otherSession = this.showSideA ? this.sessionB : this.sessionA;
    };
    BaseDiffView.prototype.$attachSessionsEventHandlers = function () {
    };
    BaseDiffView.prototype.$detachSessionsEventHandlers = function () {
    };
    BaseDiffView.prototype.getDiffSession = function () {
        return this.diffSession;
    };
    BaseDiffView.prototype.setTheme = function (theme) {
        this.editorA && this.editorA.setTheme(theme);
        this.editorB && this.editorB.setTheme(theme);
    };
    BaseDiffView.prototype.getTheme = function () {
        return (this.editorA || this.editorB).getTheme();
    };
    BaseDiffView.prototype.onChangeTheme = function (e) {
        var theme = e && e.theme || this.getTheme();
        if (this.editorA && this.editorA.getTheme() !== theme) {
            this.editorA.setTheme(theme);
        }
        if (this.editorB && this.editorB.getTheme() !== theme) {
            this.editorB.setTheme(theme);
        }
    };
    BaseDiffView.prototype.resize = function (force) {
        this.editorA && this.editorA.resize(force);
        this.editorB && this.editorB.resize(force);
    };
    BaseDiffView.prototype.scheduleOnInput = function () {
        var _this = this;
        if (this.$onInputTimer)
            return;
        this.$onInputTimer = setTimeout(function () {
            _this.$onInputTimer = null;
            _this.onInput();
        });
    };
    BaseDiffView.prototype.onInput = function () {
        var _this = this;
        if (this.$onInputTimer)
            clearTimeout(this.$onInputTimer);
        var val1 = this.sessionA.doc.getAllLines();
        var val2 = this.sessionB.doc.getAllLines();
        this.selectionRangeA = null;
        this.selectionRangeB = null;
        var chunks = this.$diffLines(val1, val2);
        this.diffSession.chunks = this.chunks = chunks;
        this.gutterDecoratorA && this.gutterDecoratorA.setDecorations(chunks);
        this.gutterDecoratorB && this.gutterDecoratorB.setDecorations(chunks);
        if (this.chunks && this.chunks.length > this.$maxDiffs) {
            return;
        }
        this.align();
        this.editorA && this.editorA.renderer.updateBackMarkers();
        this.editorB && this.editorB.renderer.updateBackMarkers();
        setTimeout(function () {
            _this.updateScrollBarDecorators();
        }, 0);
        if (this.$foldUnchangedOnInput) {
            this.foldUnchanged();
        }
    };
    BaseDiffView.prototype.setupScrollbars = function () {
        var _this = this;
        var setupScrollBar = function (renderer) {
            setTimeout(function () {
                _this.$setScrollBarDecorators(renderer);
                _this.updateScrollBarDecorators();
            }, 0);
        };
        if (this.inlineDiffEditor) {
            setupScrollBar(this.activeEditor.renderer);
        }
        else {
            setupScrollBar(this.editorA.renderer);
            setupScrollBar(this.editorB.renderer);
        }
    };
    BaseDiffView.prototype.$setScrollBarDecorators = function (renderer) {
        if (renderer.$scrollDecorator) {
            renderer.$scrollDecorator.destroy();
        }
        renderer.$scrollDecorator = new ScrollDiffDecorator(renderer.scrollBarV, renderer, this.inlineDiffEditor);
        renderer.$scrollDecorator.setSessions(this.sessionA, this.sessionB);
        renderer.scrollBarV.setVisible(true);
        renderer.scrollBarV.element.style.bottom = renderer.scrollBarH.getHeight() + "px";
    };
    BaseDiffView.prototype.$resetDecorators = function (renderer) {
        if (renderer.$scrollDecorator) {
            renderer.$scrollDecorator.destroy();
        }
        renderer.$scrollDecorator = new Decorator(renderer.scrollBarV, renderer);
    };
    BaseDiffView.prototype.updateScrollBarDecorators = function () {
        var _this = this;
        if (this.inlineDiffEditor) {
            if (!this.activeEditor) {
                return;
            }
            this.activeEditor.renderer.$scrollDecorator.$zones = [];
        }
        else {
            if (!this.editorA || !this.editorB) {
                return;
            }
            this.editorA.renderer.$scrollDecorator.$zones = [];
            this.editorB.renderer.$scrollDecorator.$zones = [];
        }
        var updateDecorators = function (editor, change) {
            if (!editor) {
                return;
            }
            if (typeof editor.renderer.$scrollDecorator.addZone !== "function") {
                return;
            }
            if (change.old.start.row != change.old.end.row) {
                editor.renderer.$scrollDecorator.addZone(change.old.start.row, change.old.end.row - 1, "delete");
            }
            if (change.new.start.row != change.new.end.row) {
                editor.renderer.$scrollDecorator.addZone(change.new.start.row, change.new.end.row - 1, "insert");
            }
        };
        if (this.inlineDiffEditor) {
            this.chunks && this.chunks.forEach(function (lineChange) {
                updateDecorators(_this.activeEditor, lineChange);
            });
            this.activeEditor.renderer.$scrollDecorator.$updateDecorators(this.activeEditor.renderer.layerConfig);
        }
        else {
            this.chunks && this.chunks.forEach(function (lineChange) {
                updateDecorators(_this.editorA, lineChange);
                updateDecorators(_this.editorB, lineChange);
            });
            this.editorA.renderer.$scrollDecorator.$updateDecorators(this.editorA.renderer.layerConfig);
            this.editorB.renderer.$scrollDecorator.$updateDecorators(this.editorB.renderer.layerConfig);
        }
    };
    BaseDiffView.prototype.$diffLines = function (val1, val2) {
        return this.diffProvider.compute(val1, val2, {
            ignoreTrimWhitespace: this.$ignoreTrimWhitespace,
            maxComputationTimeMs: this.$maxComputationTimeMs
        });
    };
    BaseDiffView.prototype.setProvider = function (provider) {
        this.diffProvider = provider;
    };
    BaseDiffView.prototype.$addWidget = function (session, w) {
        var lineWidget = session.lineWidgets[w.row];
        if (lineWidget) {
            w.rowsAbove += lineWidget.rowsAbove > w.rowsAbove ? lineWidget.rowsAbove : w.rowsAbove;
            w.rowCount += lineWidget.rowCount;
        }
        session.lineWidgets[w.row] = w;
        session.widgetManager.lineWidgets[w.row] = w;
        session.$resetRowCache(w.row);
        var fold = session.getFoldAt(w.row, 0);
        if (fold) {
            session.widgetManager.updateOnFold({
                data: fold,
                action: "add",
            }, session);
        }
    };
    BaseDiffView.prototype.$initWidgets = function (editor) {
        var session = editor.session;
        if (!session.widgetManager) {
            session.widgetManager = new LineWidgets(session);
            session.widgetManager.attach(editor);
        }
        editor.session.lineWidgets = [];
        editor.session.widgetManager.lineWidgets = [];
        editor.session.$resetRowCache(0);
    };
    BaseDiffView.prototype.$screenRow = function (pos, session) {
        var row = session.documentToScreenPosition(pos).row;
        var afterEnd = pos.row - session.getLength() + 1;
        if (afterEnd > 0) {
            row += afterEnd;
        }
        return row;
    };
    BaseDiffView.prototype.align = function () { };
    BaseDiffView.prototype.onChangeWrapLimit = function (e, session) { };
    BaseDiffView.prototype.onSelect = function (e, selection) {
        this.searchHighlight(selection);
        this.syncSelect(selection);
    };
    BaseDiffView.prototype.syncSelect = function (selection) {
        if (this.$updatingSelection)
            return;
        var isOld = selection.session === this.sessionA;
        var selectionRange = selection.getRange();
        var currSelectionRange = isOld ? this.selectionRangeA : this.selectionRangeB;
        if (currSelectionRange && selectionRange.isEqual(currSelectionRange))
            return;
        if (isOld) {
            this.selectionRangeA = selectionRange;
        }
        else {
            this.selectionRangeB = selectionRange;
        }
        this.$updatingSelection = true;
        var newRange = this.transformRange(selectionRange, isOld);
        if (this.$syncSelections) {
            (isOld ? this.editorB : this.editorA).session.selection.setSelectionRange(newRange);
        }
        this.$updatingSelection = false;
        if (isOld) {
            this.selectionRangeA = selectionRange;
            this.selectionRangeB = newRange;
        }
        else {
            this.selectionRangeA = newRange;
            this.selectionRangeB = selectionRange;
        }
        this.updateSelectionMarker(this.syncSelectionMarkerA, this.sessionA, this.selectionRangeA);
        this.updateSelectionMarker(this.syncSelectionMarkerB, this.sessionB, this.selectionRangeB);
    };
    BaseDiffView.prototype.updateSelectionMarker = function (marker, session, range) {
        marker.setRange(range);
        session._signal("changeFrontMarker");
    };
    BaseDiffView.prototype.onChangeFold = function (ev, session) {
        var fold = ev.data;
        if (this.$syncingFold || !fold || !ev.action)
            return;
        this.scheduleRealign();
        var isOrig = session === this.sessionA;
        var other = isOrig ? this.sessionB : this.sessionA;
        if (ev.action === "remove") {
            if (fold.other) {
                fold.other.other = null;
                other.removeFold(fold.other);
            }
            else if (fold.lineWidget) {
                other.widgetManager.addLineWidget(fold.lineWidget);
                fold.lineWidget = null;
                if (other["$editor"]) {
                    other["$editor"].renderer.updateBackMarkers();
                }
            }
        }
        if (ev.action === "add") {
            var range = this.transformRange(fold.range, isOrig);
            if (range.isEmpty()) {
                var row = range.start.row + 1;
                if (other.lineWidgets[row]) {
                    fold.lineWidget = other.lineWidgets[row];
                    other.widgetManager.removeLineWidget(fold.lineWidget);
                    if (other["$editor"]) {
                        other["$editor"].renderer.updateBackMarkers();
                    }
                }
            }
            else {
                this.$syncingFold = true;
                fold.other = other.addFold(fold.placeholder, range);
                if (fold.other) {
                    fold.other.other = fold;
                }
                this.$syncingFold = false;
            }
        }
    };
    BaseDiffView.prototype.scheduleRealign = function () {
        if (!this.realignPending) {
            this.realignPending = true;
            this.editorA.renderer.on("beforeRender", this.realign);
            this.editorB.renderer.on("beforeRender", this.realign);
        }
    };
    BaseDiffView.prototype.realign = function () {
        this.realignPending = true;
        this.editorA.renderer.off("beforeRender", this.realign);
        this.editorB.renderer.off("beforeRender", this.realign);
        this.align();
        this.realignPending = false;
    };
    BaseDiffView.prototype.detach = function () {
        if (!this.editorA || !this.editorB)
            return;
        if (this.savedOptionsA)
            this.editorA.setOptions(this.savedOptionsA);
        if (this.savedOptionsB)
            this.editorB.setOptions(this.savedOptionsB);
        this.editorA.renderer.off("beforeRender", this.realign);
        this.editorB.renderer.off("beforeRender", this.realign);
        this.$detachEventHandlers();
        this.$removeLineWidgets(this.sessionA);
        this.$removeLineWidgets(this.sessionB);
        this.gutterDecoratorA && this.gutterDecoratorA.dispose();
        this.gutterDecoratorB && this.gutterDecoratorB.dispose();
        this.sessionA.selection.clearSelection();
        this.sessionB.selection.clearSelection();
        if (this.savedOptionsA && this.savedOptionsA.customScrollbar) {
            this.$resetDecorators(this.editorA.renderer);
        }
        if (this.savedOptionsB && this.savedOptionsB.customScrollbar) {
            this.$resetDecorators(this.editorB.renderer);
        }
    };
    BaseDiffView.prototype.$removeLineWidgets = function (session) {
        session.lineWidgets = [];
        session.widgetManager.lineWidgets = [];
        session._signal("changeFold", { data: { start: { row: 0 } } });
    };
    BaseDiffView.prototype.$detachEventHandlers = function () {
    };
    BaseDiffView.prototype.destroy = function () {
        this.detach();
        this.editorA && this.editorA.destroy();
        this.editorB && this.editorB.destroy();
        this.editorA = this.editorB = null;
    };
    BaseDiffView.prototype.gotoNext = function (dir) {
        var ace = this.activeEditor || this.editorA;
        if (this.inlineDiffEditor) {
            ace = this.editorA;
        }
        var sideA = ace == this.editorA;
        var row = ace.selection.lead.row;
        var i = this.findChunkIndex(this.chunks, row, sideA);
        var chunk = this.chunks[i + dir] || this.chunks[i];
        var scrollTop = ace.session.getScrollTop();
        if (chunk) {
            var range = chunk[sideA ? "old" : "new"];
            var line = Math.max(range.start.row, range.end.row - 1);
            ace.selection.setRange(new Range(line, 0, line, 0));
        }
        ace.renderer.scrollSelectionIntoView(ace.selection.lead, ace.selection.anchor, 0.5);
        ace.renderer.animateScrolling(scrollTop);
    };
    BaseDiffView.prototype.firstDiffSelected = function () {
        return this.currentDiffIndex <= 1;
    };
    BaseDiffView.prototype.lastDiffSelected = function () {
        return this.currentDiffIndex > this.chunks.length - 1;
    };
    BaseDiffView.prototype.transformRange = function (range, isOriginal) {
        return Range.fromPoints(this.transformPosition(range.start, isOriginal), this.transformPosition(range.end, isOriginal));
    };
    BaseDiffView.prototype.transformPosition = function (pos, isOriginal) {
        var chunkIndex = this.findChunkIndex(this.chunks, pos.row, isOriginal);
        var chunk = this.chunks[chunkIndex];
        var clonePos = this.sessionB.doc.clonePos;
        var result = clonePos(pos);
        var _a = __read(isOriginal ? ["old", "new"] : ["new", "old"], 2), from = _a[0], to = _a[1];
        var deltaChar = 0;
        var ignoreIndent = false;
        if (chunk) {
            if (chunk[from].end.row <= pos.row) {
                result.row -= chunk[from].end.row - chunk[to].end.row;
            }
            else if (chunk.charChanges) {
                for (var i = 0; i < chunk.charChanges.length; i++) {
                    var change = chunk.charChanges[i];
                    var fromRange = change[from];
                    var toRange = change[to];
                    if (fromRange.end.row < pos.row)
                        continue;
                    if (fromRange.start.row > pos.row)
                        break;
                    if (fromRange.isMultiLine() && fromRange.contains(pos.row, pos.column)) {
                        result.row = toRange.start.row + pos.row - fromRange.start.row;
                        var maxRow = toRange.end.row;
                        if (toRange.end.column === 0)
                            maxRow--;
                        if (result.row > maxRow) {
                            result.row = maxRow;
                            result.column = (isOriginal ? this.sessionB : this.sessionA).getLine(maxRow).length;
                            ignoreIndent = true;
                        }
                        result.row = Math.min(result.row, maxRow);
                    }
                    else {
                        result.row = toRange.start.row;
                        if (fromRange.start.column > pos.column)
                            break;
                        ignoreIndent = true;
                        if (!fromRange.isEmpty() && fromRange.contains(pos.row, pos.column)) {
                            result.column = toRange.start.column;
                            deltaChar = pos.column - fromRange.start.column;
                            deltaChar = Math.min(deltaChar, toRange.end.column - toRange.start.column);
                        }
                        else {
                            result = clonePos(toRange.end);
                            deltaChar = pos.column - fromRange.end.column;
                        }
                    }
                }
            }
            else if (chunk[from].start.row <= pos.row) {
                result.row += chunk[to].start.row - chunk[from].start.row;
                if (result.row >= chunk[to].end.row) {
                    result.row = chunk[to].end.row - 1;
                    result.column = (isOriginal ? this.sessionB : this.sessionA).getLine(result.row).length;
                }
            }
        }
        if (!ignoreIndent) { //TODO:
            var _b = __read(isOriginal ? [this.sessionA, this.sessionB] : [
                this.sessionB, this.sessionA
            ], 2), fromEditSession = _b[0], toEditSession = _b[1];
            deltaChar -= this.$getDeltaIndent(fromEditSession, toEditSession, pos.row, result.row);
        }
        result.column += deltaChar;
        return result;
    };
    BaseDiffView.prototype.$getDeltaIndent = function (fromEditSession, toEditSession, fromLine, toLine) {
        var origIndent = this.$getIndent(fromEditSession, fromLine);
        var editIndent = this.$getIndent(toEditSession, toLine);
        return origIndent - editIndent;
    };
    BaseDiffView.prototype.$getIndent = function (editSession, line) {
        return editSession.getLine(line).match(/^\s*/)[0].length;
    };
    BaseDiffView.prototype.printDiffs = function () {
        this.chunks.forEach(function (diff) {
            console.log(diff.toString());
        });
    };
    BaseDiffView.prototype.findChunkIndex = function (chunks, row, isOriginal) {
        for (var i = 0; i < chunks.length; i++) {
            var ch = chunks[i];
            var chunk = isOriginal ? ch.old : ch.new;
            if (chunk.end.row < row)
                continue;
            if (chunk.start.row > row)
                break;
        }
        this.currentDiffIndex = i;
        return i - 1;
    };
    BaseDiffView.prototype.searchHighlight = function (selection) {
        if (this.$syncSelections || this.inlineDiffEditor) {
            return;
        }
        var currSession = selection.session;
        var otherSession = currSession === this.sessionA
            ? this.sessionB : this.sessionA;
        otherSession.highlight(currSession.$searchHighlight.regExp);
        otherSession._signal("changeBackMarker");
    };
    BaseDiffView.prototype.initSelectionMarkers = function () {
        this.syncSelectionMarkerA = new SyncSelectionMarker();
        this.syncSelectionMarkerB = new SyncSelectionMarker();
        this.sessionA.addDynamicMarker(this.syncSelectionMarkerA, true);
        this.sessionB.addDynamicMarker(this.syncSelectionMarkerB, true);
    };
    BaseDiffView.prototype.clearSelectionMarkers = function () {
        this.sessionA.removeMarker(this.syncSelectionMarkerA.id);
        this.sessionB.removeMarker(this.syncSelectionMarkerB.id);
    };
    return BaseDiffView;
}());
config.defineOptions(BaseDiffView.prototype, "DiffView", {
    showOtherLineNumbers: {
        set: function (value) {
            if (this.gutterLayer) {
                this.gutterLayer.$renderer = value ? null : emptyGutterRenderer;
                this.editorA.renderer.updateFull();
            }
        },
        initialValue: true
    },
    folding: {
        set: function (value) {
            this.editorA.setOption("showFoldWidgets", value);
            this.editorB.setOption("showFoldWidgets", value);
            if (!value) {
                var posA = [];
                var posB = [];
                if (this.chunks) {
                    this.chunks.forEach(function (x) {
                        posA.push(x.old.start, x.old.end);
                        posB.push(x.new.start, x.new.end);
                    });
                }
                this.sessionA.unfold(posA);
                this.sessionB.unfold(posB);
            }
        }
    },
    syncSelections: {
        set: function (value) {
        },
    },
    ignoreTrimWhitespace: {
        set: function (value) {
            this.scheduleOnInput();
        },
    },
    wrap: {
        set: function (value) {
            this.sessionA.setOption("wrap", value);
            this.sessionB.setOption("wrap", value);
        }
    },
    maxDiffs: {
        value: 5000,
    },
    theme: {
        set: function (value) {
            this.setTheme(value);
        },
        get: function () {
            return this.editorA.getTheme();
        }
    },
});
var emptyGutterRenderer = {
    getText: function name(params) {
        return "";
    },
    getWidth: function () {
        return 0;
    }
};
exports.BaseDiffView = BaseDiffView;
var DiffChunk = /** @class */ (function () {
    function DiffChunk(originalRange, modifiedRange, charChanges) {
        this.old = originalRange;
        this.new = modifiedRange;
        this.charChanges = charChanges && charChanges.map(function (m) { return new DiffChunk(new Range(m.originalStartLineNumber, m.originalStartColumn, m.originalEndLineNumber, m.originalEndColumn), new Range(m.modifiedStartLineNumber, m.modifiedStartColumn, m.modifiedEndLineNumber, m.modifiedEndColumn)); });
    }
    return DiffChunk;
}());
var DiffHighlight = /** @class */ (function () {
    function DiffHighlight(diffView, type) { this.id;
        this.diffView = diffView;
        this.type = type;
    }
    DiffHighlight.prototype.update = function (html, markerLayer, session, config) {
        var dir, operation, opOperation;
        var diffView = this.diffView;
        if (this.type === -1) { // original editor
            dir = "old";
            operation = "delete";
            opOperation = "insert";
        }
        else { //modified editor
            dir = "new";
            operation = "insert";
            opOperation = "delete";
        }
        var ignoreTrimWhitespace = diffView.$ignoreTrimWhitespace;
        var lineChanges = diffView.chunks;
        if (session.lineWidgets && !diffView.inlineDiffEditor) {
            for (var row = config.firstRow; row <= config.lastRow; row++) {
                var lineWidget = session.lineWidgets[row];
                if (!lineWidget || lineWidget.hidden)
                    continue;
                var start = session.documentToScreenRow(row, 0);
                if (lineWidget.rowsAbove > 0) {
                    var range = new Range(start - lineWidget.rowsAbove, 0, start - 1, Number.MAX_VALUE);
                    markerLayer.drawFullLineMarker(html, range, "ace_diff aligned_diff", config);
                }
                var end = start + lineWidget.rowCount - (lineWidget.rowsAbove || 0);
                var range = new Range(start + 1, 0, end, Number.MAX_VALUE);
                markerLayer.drawFullLineMarker(html, range, "ace_diff aligned_diff", config);
            }
        }
        lineChanges.forEach(function (lineChange) {
            var startRow = lineChange[dir].start.row;
            var endRow = lineChange[dir].end.row;
            if (endRow < config.firstRow || startRow > config.lastRow)
                return;
            var range = new Range(startRow, 0, endRow - 1, 1 << 30);
            if (startRow !== endRow) {
                range = range.toScreenRange(session);
                markerLayer.drawFullLineMarker(html, range, "ace_diff " + operation, config);
            }
            if (lineChange.charChanges) {
                for (var i = 0; i < lineChange.charChanges.length; i++) {
                    var changeRange = lineChange.charChanges[i][dir];
                    if (changeRange.end.column == 0 && changeRange.end.row > changeRange.start.row && changeRange.end.row == lineChange[dir].end.row) {
                        changeRange.end.row--;
                        changeRange.end.column = Number.MAX_VALUE;
                    }
                    if (ignoreTrimWhitespace) {
                        for (var lineNumber = changeRange.start.row; lineNumber <= changeRange.end.row; lineNumber++) {
                            var startColumn = void 0;
                            var endColumn = void 0;
                            var sessionLineStart = session.getLine(lineNumber).match(/^\s*/)[0].length;
                            var sessionLineEnd = session.getLine(lineNumber).length;
                            if (lineNumber === changeRange.start.row) {
                                startColumn = changeRange.start.column;
                            }
                            else {
                                startColumn = sessionLineStart;
                            }
                            if (lineNumber === changeRange.end.row) {
                                endColumn = changeRange.end.column;
                            }
                            else {
                                endColumn = sessionLineEnd;
                            }
                            var range_1 = new Range(lineNumber, startColumn, lineNumber, endColumn);
                            var screenRange = range_1.toScreenRange(session);
                            if (sessionLineStart === startColumn && sessionLineEnd === endColumn) {
                                continue;
                            }
                            var cssClass = "inline " + operation;
                            if (range_1.isEmpty() && startColumn !== 0) {
                                cssClass = "inline " + opOperation + " empty";
                            }
                            markerLayer.drawSingleLineMarker(html, screenRange, "ace_diff " + cssClass, config);
                        }
                    }
                    else {
                        var range_2 = new Range(changeRange.start.row, changeRange.start.column, changeRange.end.row, changeRange.end.column);
                        var screenRange = range_2.toScreenRange(session);
                        var cssClass = "inline " + operation;
                        if (range_2.isEmpty() && changeRange.start.column !== 0) {
                            cssClass = "inline empty " + opOperation;
                        }
                        if (screenRange.isMultiLine()) {
                            markerLayer.drawTextMarker(html, screenRange, "ace_diff " + cssClass, config);
                        }
                        else {
                            markerLayer.drawSingleLineMarker(html, screenRange, "ace_diff " + cssClass, config);
                        }
                    }
                }
            }
        });
    };
    return DiffHighlight;
}());
var SyncSelectionMarker = /** @class */ (function () {
    function SyncSelectionMarker() { this.id;
        this.type = "fullLine";
        this.clazz = "ace_diff-active-line";
    }
    SyncSelectionMarker.prototype.update = function (html, markerLayer, session, config) {
    };
    SyncSelectionMarker.prototype.setRange = function (range) {
        var newRange = range.clone();
        newRange.end.column++;
        this.range = newRange;
    };
    return SyncSelectionMarker;
}());
exports.DiffChunk = DiffChunk;
exports.DiffHighlight = DiffHighlight;

});

define("ace/ext/diff/inline_diff_view",["require","exports","module","ace/ext/diff/base_diff_view","ace/virtual_renderer","ace/config"], function(require, exports, module){"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (Object.prototype.hasOwnProperty.call(b, p)) d[p] = b[p]; };
        return extendStatics(d, b);
    };
    return function (d, b) {
        if (typeof b !== "function" && b !== null)
            throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
var BaseDiffView = require("./base_diff_view").BaseDiffView;
var Renderer = require("../../virtual_renderer").VirtualRenderer;
var config = require("../../config");
var InlineDiffView = /** @class */ (function (_super) {
    __extends(InlineDiffView, _super);
    function InlineDiffView(diffModel, container) {
        var _this = this;
        diffModel = diffModel || {};
        diffModel.inline = diffModel.inline || "a";
        _this = _super.call(this, true, container) || this;
        _this.init(diffModel);
        return _this;
    }
    InlineDiffView.prototype.init = function (diffModel) {
        this.onSelect = this.onSelect.bind(this);
        this.onAfterRender = this.onAfterRender.bind(this);
        this.$setupModels(diffModel);
        this.onChangeTheme();
        config.resetOptions(this);
        config["_signal"]("diffView", this);
        var padding = this.activeEditor.renderer.$padding;
        this.addGutterDecorators();
        this.otherEditor.renderer.setPadding(padding);
        this.textLayer = this.otherEditor.renderer.$textLayer;
        this.markerLayer = this.otherEditor.renderer.$markerBack;
        this.gutterLayer = this.otherEditor.renderer.$gutterLayer;
        this.cursorLayer = this.otherEditor.renderer.$cursorLayer;
        this.otherEditor.renderer.$updateCachedSize = function () {
        };
        var textLayerElement = this.activeEditor.renderer.$textLayer.element;
        textLayerElement.parentNode.insertBefore(this.textLayer.element, textLayerElement);
        var markerLayerElement = this.activeEditor.renderer.$markerBack.element;
        markerLayerElement.parentNode.insertBefore(this.markerLayer.element, markerLayerElement.nextSibling);
        var gutterLayerElement = this.activeEditor.renderer.$gutterLayer.element;
        gutterLayerElement.parentNode.insertBefore(this.gutterLayer.element, gutterLayerElement.nextSibling);
        gutterLayerElement.style.position = "absolute";
        this.gutterLayer.element.style.position = "absolute";
        this.gutterLayer.element.style.width = "100%";
        this.gutterLayer.element.classList.add("ace_mini-diff_gutter_other");
        this.gutterLayer.$updateGutterWidth = function () { };
        this.initMouse();
        this.initTextInput();
        this.initTextLayer();
        this.initRenderer();
        this.$attachEventHandlers();
        this.selectEditor(this.activeEditor);
    };
    InlineDiffView.prototype.initRenderer = function (restore) {
        var _this = this;
        if (restore) {
            delete this.activeEditor.renderer.$getLongestLine;
        }
        else {
            this.editorA.renderer.$getLongestLine =
                this.editorB.renderer.$getLongestLine = function () {
                    var getLongestLine = Renderer.prototype.$getLongestLine;
                    return Math.max(getLongestLine.call(_this.editorA.renderer), getLongestLine.call(_this.editorB.renderer));
                };
        }
    };
    InlineDiffView.prototype.initTextLayer = function () {
        var renderLine = this.textLayer.$renderLine;
        var diffView = this;
        this.otherEditor.renderer.$textLayer.$renderLine = function (parent, row, foldLIne) {
            if (isVisibleRow(diffView.chunks, row)) {
                renderLine.call(this, parent, row, foldLIne);
            }
        };
        var side = this.showSideA ? "new" : "old";
        function isVisibleRow(chunks, row) {
            var min = 0;
            var max = chunks.length - 1;
            var result = -1;
            while (min < max) {
                var mid = Math.floor((min + max) / 2);
                var chunkStart = chunks[mid][side].start.row;
                if (chunkStart < row) {
                    result = mid;
                    min = mid + 1;
                }
                else if (chunkStart > row) {
                    max = mid - 1;
                }
                else {
                    result = mid;
                    break;
                }
            }
            if (chunks[result + 1] && chunks[result + 1][side].start.row <= row) {
                result++;
            }
            var range = chunks[result] && chunks[result][side];
            if (range && range.end.row > row) {
                return true;
            }
            return false;
        }
    };
    InlineDiffView.prototype.initTextInput = function (restore) {
        if (restore) {
            this.otherEditor.textInput = this.othertextInput;
            this.otherEditor.container = this.otherEditorContainer;
        }
        else {
            this.othertextInput = this.otherEditor.textInput;
            this.otherEditor.textInput = this.activeEditor.textInput;
            this.otherEditorContainer = this.otherEditor.container;
            this.otherEditor.container = this.activeEditor.container;
        }
    };
    InlineDiffView.prototype.selectEditor = function (editor) {
        if (editor == this.activeEditor) {
            this.otherEditor.selection.clearSelection();
            this.activeEditor.textInput.setHost(this.activeEditor);
            this.activeEditor.setStyle("ace_diff_other", false);
            this.cursorLayer.element.remove();
            this.activeEditor.renderer.$cursorLayer.element.style.display = "block";
            if (this.showSideA) {
                this.sessionA.removeMarker(this.syncSelectionMarkerA.id);
                this.sessionA.addDynamicMarker(this.syncSelectionMarkerA, true);
            }
            this.markerLayer.element.classList.add("ace_hidden_marker-layer");
            this.activeEditor.renderer.$markerBack.element.classList.remove("ace_hidden_marker-layer");
            this.removeBracketHighlight(this.otherEditor);
        }
        else {
            this.activeEditor.selection.clearSelection();
            this.activeEditor.textInput.setHost(this.otherEditor);
            this.activeEditor.setStyle("ace_diff_other");
            this.activeEditor.renderer.$cursorLayer.element.parentNode.appendChild(this.cursorLayer.element);
            this.activeEditor.renderer.$cursorLayer.element.style.display = "none";
            if (this.activeEditor.$isFocused) {
                this.otherEditor.onFocus();
            }
            if (this.showSideA) {
                this.sessionA.removeMarker(this.syncSelectionMarkerA.id);
            }
            this.markerLayer.element.classList.remove("ace_hidden_marker-layer");
            this.activeEditor.renderer.$markerBack.element.classList.add("ace_hidden_marker-layer");
            this.removeBracketHighlight(this.activeEditor);
        }
    };
    InlineDiffView.prototype.removeBracketHighlight = function (editor) {
        var session = editor.session;
        if (session.$bracketHighlight) {
            session.$bracketHighlight.markerIds.forEach(function (id) {
                session.removeMarker(id);
            });
            session.$bracketHighlight = null;
        }
    };
    InlineDiffView.prototype.initMouse = function () {
        var _this = this;
        this.otherEditor.renderer.$loop = this.activeEditor.renderer.$loop;
        this.otherEditor.renderer.scroller = {
            getBoundingClientRect: function () {
                return _this.activeEditor.renderer.scroller.getBoundingClientRect();
            },
            style: this.activeEditor.renderer.scroller.style,
        };
        var forwardEvent = function (ev) {
            if (!ev.domEvent)
                return;
            var screenPos = ev.editor.renderer.pixelToScreenCoordinates(ev.clientX, ev.clientY);
            var sessionA = _this.activeEditor.session;
            var sessionB = _this.otherEditor.session;
            var posA = sessionA.screenToDocumentPosition(screenPos.row, screenPos.column, screenPos.offsetX);
            var posB = sessionB.screenToDocumentPosition(screenPos.row, screenPos.column, screenPos.offsetX);
            var posAx = sessionA.documentToScreenPosition(posA);
            var posBx = sessionB.documentToScreenPosition(posB);
            if (ev.editor == _this.activeEditor) {
                if (posBx.row == screenPos.row && posAx.row != screenPos.row) {
                    if (ev.type == "mousedown") {
                        _this.selectEditor(_this.otherEditor);
                    }
                    ev.propagationStopped = true;
                    ev.defaultPrevented = true;
                    _this.otherEditor.$mouseHandler.onMouseEvent(ev.type, ev.domEvent);
                }
                else if (ev.type == "mousedown") {
                    _this.selectEditor(_this.activeEditor);
                }
            }
        };
        var events = [
            "mousedown",
            "click",
            "mouseup",
            "dblclick",
            "tripleclick",
            "quadclick",
        ];
        events.forEach(function (event) {
            _this.activeEditor.on(event, forwardEvent, true);
            _this.activeEditor.on("gutter" + event, forwardEvent, true);
        });
        var onFocus = function (e) {
            _this.activeEditor.onFocus(e);
        };
        var onBlur = function (e) {
            _this.activeEditor.onBlur(e);
        };
        this.otherEditor.on("focus", onFocus);
        this.otherEditor.on("blur", onBlur);
        this.onMouseDetach = function () {
            events.forEach(function (event) {
                _this.activeEditor.off(event, forwardEvent, true);
                _this.activeEditor.off("gutter" + event, forwardEvent, true);
            });
            _this.otherEditor.off("focus", onFocus);
            _this.otherEditor.off("blur", onBlur);
        };
    };
    InlineDiffView.prototype.align = function () {
        var diffView = this;
        this.$initWidgets(diffView.editorA);
        this.$initWidgets(diffView.editorB);
        diffView.chunks.forEach(function (ch) {
            var diff1 = diffView.$screenRow(ch.old.end, diffView.sessionA)
                - diffView.$screenRow(ch.old.start, diffView.sessionA);
            var diff2 = diffView.$screenRow(ch.new.end, diffView.sessionB)
                - diffView.$screenRow(ch.new.start, diffView.sessionB);
            diffView.$addWidget(diffView.sessionA, {
                rowCount: diff2,
                rowsAbove: ch.old.end.row === 0 ? diff2 : 0,
                row: ch.old.end.row === 0 ? 0 : ch.old.end.row - 1
            });
            diffView.$addWidget(diffView.sessionB, {
                rowCount: diff1,
                rowsAbove: diff1,
                row: ch.new.start.row,
            });
        });
        diffView.sessionA["_emit"]("changeFold", { data: { start: { row: 0 } } });
        diffView.sessionB["_emit"]("changeFold", { data: { start: { row: 0 } } });
    };
    InlineDiffView.prototype.onChangeWrapLimit = function (e, session) {
        this.otherSession.setOption("wrap", session.getOption("wrap"));
        this.otherSession.adjustWrapLimit(session.$wrapLimit);
        this.scheduleRealign();
        this.activeEditor.renderer.updateFull();
    };
    InlineDiffView.prototype.$attachSessionsEventHandlers = function () {
        this.$attachSessionEventHandlers(this.editorA, this.markerA);
        this.$attachSessionEventHandlers(this.editorB, this.markerB);
        var session = this.activeEditor.session;
        session.on("changeWrapLimit", this.onChangeWrapLimit);
        session.on("changeWrapMode", this.onChangeWrapLimit);
    };
    InlineDiffView.prototype.$attachSessionEventHandlers = function (editor, marker) {
        editor.session.on("changeFold", this.onChangeFold);
        editor.session.addDynamicMarker(marker);
        editor.selection.on("changeCursor", this.onSelect);
        editor.selection.on("changeSelection", this.onSelect);
    };
    InlineDiffView.prototype.$detachSessionsEventHandlers = function () {
        this.$detachSessionHandlers(this.editorA, this.markerA);
        this.$detachSessionHandlers(this.editorB, this.markerB);
        this.otherSession.bgTokenizer.lines.fill(undefined);
        var session = this.activeEditor.session;
        session.off("changeWrapLimit", this.onChangeWrapLimit);
        session.off("changeWrapMode", this.onChangeWrapLimit);
    };
    InlineDiffView.prototype.$detachSessionHandlers = function (editor, marker) {
        editor.session.removeMarker(marker.id);
        editor.selection.off("changeCursor", this.onSelect);
        editor.selection.off("changeSelection", this.onSelect);
        editor.session.off("changeFold", this.onChangeFold);
    };
    InlineDiffView.prototype.$attachEventHandlers = function () {
        this.activeEditor.on("input", this.onInput);
        this.activeEditor.renderer.on("afterRender", this.onAfterRender);
        this.otherSession.on("change", this.onInput);
    };
    InlineDiffView.prototype.$detachEventHandlers = function () {
        this.$detachSessionsEventHandlers();
        this.activeEditor.off("input", this.onInput);
        this.activeEditor.renderer.off("afterRender", this.onAfterRender);
        this.otherSession.off("change", this.onInput);
        this.textLayer.element.textContent = "";
        this.textLayer.element.remove();
        this.gutterLayer.element.textContent = "";
        this.gutterLayer.element.remove();
        this.markerLayer.element.textContent = "";
        this.markerLayer.element.remove();
        this.onMouseDetach();
        this.selectEditor(this.activeEditor);
        this.clearSelectionMarkers();
        this.otherEditor.setSession(null);
        this.otherEditor.renderer.$loop = null;
        this.initTextInput(true);
        this.initRenderer(true);
        this.otherEditor.destroy();
    };
    InlineDiffView.prototype.onAfterRender = function (changes, renderer) {
        var config = renderer.layerConfig;
        var session = this.otherSession;
        var cloneRenderer = this.otherEditor.renderer;
        session.$scrollTop = renderer.scrollTop;
        session.$scrollLeft = renderer.scrollLeft;
        [
            "characterWidth",
            "lineHeight",
            "scrollTop",
            "scrollLeft",
            "scrollMargin",
            "$padding",
            "$size",
            "layerConfig",
            "$horizScroll",
            "$vScroll",
        ].forEach(function (prop) {
            cloneRenderer[prop] = renderer[prop];
        });
        cloneRenderer.$computeLayerConfig();
        var newConfig = cloneRenderer.layerConfig;
        this.gutterLayer.update(newConfig);
        newConfig.firstRowScreen = config.firstRowScreen;
        cloneRenderer.$cursorLayer.config = newConfig;
        cloneRenderer.$cursorLayer.update(newConfig);
        if (changes & cloneRenderer.CHANGE_LINES
            || changes & cloneRenderer.CHANGE_FULL
            || changes & cloneRenderer.CHANGE_SCROLL
            || changes & cloneRenderer.CHANGE_TEXT)
            this.textLayer.update(newConfig);
        this.markerLayer.setMarkers(this.otherSession.getMarkers());
        this.markerLayer.update(newConfig);
    };
    InlineDiffView.prototype.detach = function () {
        _super.prototype.detach.call(this);
        this.otherEditor && this.otherEditor.destroy();
    };
    return InlineDiffView;
}(BaseDiffView));
exports.InlineDiffView = InlineDiffView;

});

define("ace/ext/diff/split_diff_view",["require","exports","module","ace/ext/diff/base_diff_view","ace/config"], function(require, exports, module){"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (Object.prototype.hasOwnProperty.call(b, p)) d[p] = b[p]; };
        return extendStatics(d, b);
    };
    return function (d, b) {
        if (typeof b !== "function" && b !== null)
            throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
var BaseDiffView = require("./base_diff_view").BaseDiffView;
var config = require("../../config");
var SplitDiffView = /** @class */ (function (_super) {
    __extends(SplitDiffView, _super);
    function SplitDiffView(diffModel) {
        var _this = this;
        diffModel = diffModel || {};
        _this = _super.call(this) || this;
        _this.init(diffModel);
        return _this;
    }
    SplitDiffView.prototype.init = function (diffModel) {
        this.onChangeTheme = this.onChangeTheme.bind(this);
        this.onMouseWheel = this.onMouseWheel.bind(this);
        this.onScroll = this.onScroll.bind(this);
        this.$setupModels(diffModel);
        this.addGutterDecorators();
        this.onChangeTheme();
        config.resetOptions(this);
        config["_signal"]("diffView", this);
        this.$attachEventHandlers();
    };
    SplitDiffView.prototype.onChangeWrapLimit = function () {
        this.scheduleRealign();
    };
    SplitDiffView.prototype.align = function () {
        var diffView = this;
        this.$initWidgets(diffView.editorA);
        this.$initWidgets(diffView.editorB);
        diffView.chunks.forEach(function (ch) {
            var diff1 = diffView.$screenRow(ch.old.start, diffView.sessionA);
            var diff2 = diffView.$screenRow(ch.new.start, diffView.sessionB);
            if (diff1 < diff2) {
                diffView.$addWidget(diffView.sessionA, {
                    rowCount: diff2 - diff1,
                    rowsAbove: ch.old.start.row === 0 ? diff2 - diff1 : 0,
                    row: ch.old.start.row === 0 ? 0 : ch.old.start.row - 1
                });
            }
            else if (diff1 > diff2) {
                diffView.$addWidget(diffView.sessionB, {
                    rowCount: diff1 - diff2,
                    rowsAbove: ch.new.start.row === 0 ? diff1 - diff2 : 0,
                    row: ch.new.start.row === 0 ? 0 : ch.new.start.row - 1
                });
            }
            var diff1 = diffView.$screenRow(ch.old.end, diffView.sessionA);
            var diff2 = diffView.$screenRow(ch.new.end, diffView.sessionB);
            if (diff1 < diff2) {
                diffView.$addWidget(diffView.sessionA, {
                    rowCount: diff2 - diff1,
                    rowsAbove: ch.old.end.row === 0 ? diff2 - diff1 : 0,
                    row: ch.old.end.row === 0 ? 0 : ch.old.end.row - 1
                });
            }
            else if (diff1 > diff2) {
                diffView.$addWidget(diffView.sessionB, {
                    rowCount: diff1 - diff2,
                    rowsAbove: ch.new.end.row === 0 ? diff1 - diff2 : 0,
                    row: ch.new.end.row === 0 ? 0 : ch.new.end.row - 1
                });
            }
        });
        diffView.sessionA["_emit"]("changeFold", { data: { start: { row: 0 } } });
        diffView.sessionB["_emit"]("changeFold", { data: { start: { row: 0 } } });
    };
    SplitDiffView.prototype.onScroll = function (e, session) {
        this.syncScroll(this.sessionA === session ? this.editorA.renderer : this.editorB.renderer);
    };
    SplitDiffView.prototype.syncScroll = function (renderer) {
        if (this.$syncScroll == false)
            return;
        var r1 = this.editorA.renderer;
        var r2 = this.editorB.renderer;
        var isOrig = renderer == r1;
        if (r1["$scrollAnimation"] && r2["$scrollAnimation"])
            return;
        var now = Date.now();
        if (this.scrollSetBy != renderer && now - this.scrollSetAt < 500)
            return;
        var r = isOrig ? r1 : r2;
        if (this.scrollSetBy != renderer) {
            if (isOrig && this.scrollA == r.session.getScrollTop())
                return;
            else if (!isOrig && this.scrollB
                == r.session.getScrollTop())
                return;
        }
        var rOther = isOrig ? r2 : r1;
        var targetPos = r.session.getScrollTop();
        this.$syncScroll = false;
        if (isOrig) {
            this.scrollA = r.session.getScrollTop();
            this.scrollB = targetPos;
        }
        else {
            this.scrollA = targetPos;
            this.scrollB = r.session.getScrollTop();
        }
        this.scrollSetBy = renderer;
        rOther.session.setScrollTop(targetPos);
        this.$syncScroll = true;
        this.scrollSetAt = now;
    };
    SplitDiffView.prototype.onMouseWheel = function (ev) {
        if (ev.getAccelKey())
            return;
        if (ev.getShiftKey() && ev.wheelY && !ev.wheelX) {
            ev.wheelX = ev.wheelY;
            ev.wheelY = 0;
        }
        var editor = ev.editor;
        var isScrolable = editor.renderer.isScrollableBy(ev.wheelX * ev.speed, ev.wheelY * ev.speed);
        if (!isScrolable) {
            var other = editor == this.editorA ? this.editorB : this.editorA;
            if (other.renderer.isScrollableBy(ev.wheelX * ev.speed, ev.wheelY * ev.speed))
                other.renderer.scrollBy(ev.wheelX * ev.speed, ev.wheelY * ev.speed);
            return ev.stop();
        }
    };
    SplitDiffView.prototype.$attachSessionsEventHandlers = function () {
        this.$attachSessionEventHandlers(this.editorA, this.markerA);
        this.$attachSessionEventHandlers(this.editorB, this.markerB);
    };
    SplitDiffView.prototype.$attachSessionEventHandlers = function (editor, marker) {
        editor.session.on("changeScrollTop", this.onScroll);
        editor.session.on("changeFold", this.onChangeFold);
        editor.session.addDynamicMarker(marker);
        editor.selection.on("changeCursor", this.onSelect);
        editor.selection.on("changeSelection", this.onSelect);
        editor.session.on("changeWrapLimit", this.onChangeWrapLimit);
        editor.session.on("changeWrapMode", this.onChangeWrapLimit);
    };
    SplitDiffView.prototype.$detachSessionsEventHandlers = function () {
        this.$detachSessionHandlers(this.editorA, this.markerA);
        this.$detachSessionHandlers(this.editorB, this.markerB);
    };
    SplitDiffView.prototype.$detachSessionHandlers = function (editor, marker) {
        editor.session.off("changeScrollTop", this.onScroll);
        editor.session.off("changeFold", this.onChangeFold);
        editor.session.removeMarker(marker.id);
        editor.selection.off("changeCursor", this.onSelect);
        editor.selection.off("changeSelection", this.onSelect);
        editor.session.off("changeWrapLimit", this.onChangeWrapLimit);
        editor.session.off("changeWrapMode", this.onChangeWrapLimit);
    };
    SplitDiffView.prototype.$attachEventHandlers = function () {
        this.editorA.renderer.on("themeChange", this.onChangeTheme);
        this.editorB.renderer.on("themeChange", this.onChangeTheme);
        this.editorA.on("mousewheel", this.onMouseWheel);
        this.editorB.on("mousewheel", this.onMouseWheel);
        this.editorA.on("input", this.onInput);
        this.editorB.on("input", this.onInput);
    };
    SplitDiffView.prototype.$detachEventHandlers = function () {
        this.$detachSessionsEventHandlers();
        this.clearSelectionMarkers();
        this.editorA.renderer.off("themeChange", this.onChangeTheme);
        this.editorB.renderer.off("themeChange", this.onChangeTheme);
        this.$detachEditorEventHandlers(this.editorA);
        this.$detachEditorEventHandlers(this.editorB);
    };
    SplitDiffView.prototype.$detachEditorEventHandlers = function (editor) {
        editor.off("mousewheel", this.onMouseWheel);
        editor.off("input", this.onInput);
    };
    return SplitDiffView;
}(BaseDiffView));
exports.SplitDiffView = SplitDiffView;

});

define("ace/ext/diff/providers/default",["require","exports","module","ace/range","ace/ext/diff/base_diff_view"], function(require, exports, module){'use strict';
var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (Object.prototype.hasOwnProperty.call(b, p)) d[p] = b[p]; };
        return extendStatics(d, b);
    };
    return function (d, b) {
        if (typeof b !== "function" && b !== null)
            throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
var __values = (this && this.__values) || function(o) {
    var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
    if (m) return m.call(o);
    if (o && typeof o.length === "number") return {
        next: function () {
            if (o && i >= o.length) o = void 0;
            return { value: o && o[i++], done: !o };
        }
    };
    throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
};
var _a;
var _b, _c, _d, _e, _f;
function equals(one, other, itemEquals) {
    if (itemEquals === void 0) { itemEquals = function (a, b) { return a === b; }; }
    if (one === other) {
        return true;
    }
    if (!one || !other) {
        return false;
    }
    if (one.length !== other.length) {
        return false;
    }
    for (var i = 0, len = one.length; i < len; i++) {
        if (!itemEquals(one[i], other[i])) {
            return false;
        }
    }
    return true;
}
function groupAdjacentBy(items, shouldBeGrouped) {
    var currentGroup, last, items_1, items_1_1, item, e_1_1;
    var e_1, _a;
    return __generator(this, function (_g) {
        switch (_g.label) {
            case 0:
                _g.trys.push([0, 8, 9, 10]);
                items_1 = __values(items), items_1_1 = items_1.next();
                _g.label = 1;
            case 1:
                if (!!items_1_1.done) return [3 /*break*/, 7];
                item = items_1_1.value;
                if (!(last !== undefined && shouldBeGrouped(last, item))) return [3 /*break*/, 2];
                currentGroup.push(item);
                return [3 /*break*/, 5];
            case 2:
                if (!currentGroup) return [3 /*break*/, 4];
                return [4 /*yield*/, currentGroup];
            case 3:
                _g.sent();
                _g.label = 4;
            case 4:
                currentGroup = [item];
                _g.label = 5;
            case 5:
                last = item;
                _g.label = 6;
            case 6:
                items_1_1 = items_1.next();
                return [3 /*break*/, 1];
            case 7: return [3 /*break*/, 10];
            case 8:
                e_1_1 = _g.sent();
                e_1 = { error: e_1_1 };
                return [3 /*break*/, 10];
            case 9:
                try {
                    if (items_1_1 && !items_1_1.done && (_a = items_1.return)) _a.call(items_1);
                }
                finally { if (e_1) throw e_1.error; }
                return [7 /*endfinally*/];
            case 10:
                if (!currentGroup) return [3 /*break*/, 12];
                return [4 /*yield*/, currentGroup];
            case 11:
                _g.sent();
                _g.label = 12;
            case 12: return [2 /*return*/];
        }
    });
}
function forEachAdjacent(arr, f) {
    for (var i = 0; i <= arr.length; i++) {
        f(i === 0 ? undefined : arr[i - 1], i === arr.length ? undefined : arr[i]);
    }
}
function forEachWithNeighbors(arr, f) {
    for (var i = 0; i < arr.length; i++) {
        f(i === 0 ? undefined : arr[i - 1], arr[i], i + 1 === arr.length ? undefined : arr[i + 1]);
    }
}
function pushMany(arr, items) {
    var e_2, _a;
    try {
        for (var items_2 = __values(items), items_2_1 = items_2.next(); !items_2_1.done; items_2_1 = items_2.next()) {
            var item = items_2_1.value;
            arr.push(item);
        }
    }
    catch (e_2_1) { e_2 = { error: e_2_1 }; }
    finally {
        try {
            if (items_2_1 && !items_2_1.done && (_a = items_2.return)) _a.call(items_2);
        }
        finally { if (e_2) throw e_2.error; }
    }
}
function compareBy(selector, comparator) {
    return function (a, b) { return comparator(selector(a), selector(b)); };
}
var numberComparator = function (a, b) { return a - b; };
function reverseOrder(comparator) {
    return function (a, b) { return -comparator(a, b); };
}
var BugIndicatingError = /** @class */ (function (_super) {
    __extends(BugIndicatingError, _super);
    function BugIndicatingError(message) {
        var _this = _super.call(this, message || "An unexpected bug occurred.") || this;
        Object.setPrototypeOf(_this, BugIndicatingError.prototype);
        return _this;
    }
    return BugIndicatingError;
}(Error));
function assert(condition, message) {
    if (message === void 0) { message = "unexpected state"; }
    if (!condition) {
        throw new BugIndicatingError("Assertion Failed: ".concat(message));
    }
}
function assertFn(condition) {
    condition();
}
function checkAdjacentItems(items, predicate) {
    var i = 0;
    while (i < items.length - 1) {
        var a = items[i];
        var b = items[i + 1];
        if (!predicate(a, b)) {
            return false;
        }
        i++;
    }
    return true;
}
var OffsetRange = /** @class */ (function () {
    function OffsetRange(start, endExclusive) {
        this.start = start;
        this.endExclusive = endExclusive;
        if (start > endExclusive) {
            throw new BugIndicatingError("Invalid range: ".concat(this.toString()));
        }
    }
    OffsetRange.fromTo = function (start, endExclusive) {
        return new OffsetRange(start, endExclusive);
    };
    OffsetRange.addRange = function (range, sortedRanges) {
        var i = 0;
        while (i < sortedRanges.length && sortedRanges[i].endExclusive < range.start) {
            i++;
        }
        var j = i;
        while (j < sortedRanges.length && sortedRanges[j].start <= range.endExclusive) {
            j++;
        }
        if (i === j) {
            sortedRanges.splice(i, 0, range);
        }
        else {
            var start = Math.min(range.start, sortedRanges[i].start);
            var end = Math.max(range.endExclusive, sortedRanges[j - 1].endExclusive);
            sortedRanges.splice(i, j - i, new OffsetRange(start, end));
        }
    };
    OffsetRange.tryCreate = function (start, endExclusive) {
        if (start > endExclusive) {
            return undefined;
        }
        return new OffsetRange(start, endExclusive);
    };
    OffsetRange.ofLength = function (length) {
        return new OffsetRange(0, length);
    };
    OffsetRange.ofStartAndLength = function (start, length) {
        return new OffsetRange(start, start + length);
    };
    OffsetRange.emptyAt = function (offset) {
        return new OffsetRange(offset, offset);
    };
    Object.defineProperty(OffsetRange.prototype, "isEmpty", {
        get: function () {
            return this.start === this.endExclusive;
        },
        enumerable: false,
        configurable: true
    });
    OffsetRange.prototype.delta = function (offset) {
        return new OffsetRange(this.start + offset, this.endExclusive + offset);
    };
    OffsetRange.prototype.deltaStart = function (offset) {
        return new OffsetRange(this.start + offset, this.endExclusive);
    };
    OffsetRange.prototype.deltaEnd = function (offset) {
        return new OffsetRange(this.start, this.endExclusive + offset);
    };
    Object.defineProperty(OffsetRange.prototype, "length", {
        get: function () {
            return this.endExclusive - this.start;
        },
        enumerable: false,
        configurable: true
    });
    OffsetRange.prototype.toString = function () {
        return "[".concat(this.start, ", ").concat(this.endExclusive, ")");
    };
    OffsetRange.prototype.equals = function (other) {
        return this.start === other.start && this.endExclusive === other.endExclusive;
    };
    OffsetRange.prototype.containsRange = function (other) {
        return this.start <= other.start && other.endExclusive <= this.endExclusive;
    };
    OffsetRange.prototype.contains = function (offset) {
        return this.start <= offset && offset < this.endExclusive;
    };
    OffsetRange.prototype.join = function (other) {
        return new OffsetRange(Math.min(this.start, other.start), Math.max(this.endExclusive, other.endExclusive));
    };
    OffsetRange.prototype.intersect = function (other) {
        var start = Math.max(this.start, other.start);
        var end = Math.min(this.endExclusive, other.endExclusive);
        if (start <= end) {
            return new OffsetRange(start, end);
        }
        return undefined;
    };
    OffsetRange.prototype.intersectionLength = function (range) {
        var start = Math.max(this.start, range.start);
        var end = Math.min(this.endExclusive, range.endExclusive);
        return Math.max(0, end - start);
    };
    OffsetRange.prototype.intersects = function (other) {
        var start = Math.max(this.start, other.start);
        var end = Math.min(this.endExclusive, other.endExclusive);
        return start < end;
    };
    OffsetRange.prototype.intersectsOrTouches = function (other) {
        var start = Math.max(this.start, other.start);
        var end = Math.min(this.endExclusive, other.endExclusive);
        return start <= end;
    };
    OffsetRange.prototype.isBefore = function (other) {
        return this.endExclusive <= other.start;
    };
    OffsetRange.prototype.isAfter = function (other) {
        return this.start >= other.endExclusive;
    };
    OffsetRange.prototype.slice = function (arr) {
        return arr.slice(this.start, this.endExclusive);
    };
    OffsetRange.prototype.substring = function (str) {
        return str.substring(this.start, this.endExclusive);
    };
    OffsetRange.prototype.clip = function (value) {
        if (this.isEmpty) {
            throw new BugIndicatingError("Invalid clipping range: ".concat(this.toString()));
        }
        return Math.max(this.start, Math.min(this.endExclusive - 1, value));
    };
    OffsetRange.prototype.clipCyclic = function (value) {
        if (this.isEmpty) {
            throw new BugIndicatingError("Invalid clipping range: ".concat(this.toString()));
        }
        if (value < this.start) {
            return this.endExclusive - (this.start - value) % this.length;
        }
        if (value >= this.endExclusive) {
            return this.start + (value - this.start) % this.length;
        }
        return value;
    };
    OffsetRange.prototype.map = function (f) {
        var result = [];
        for (var i = this.start; i < this.endExclusive; i++) {
            result.push(f(i));
        }
        return result;
    };
    OffsetRange.prototype.forEach = function (f) {
        for (var i = this.start; i < this.endExclusive; i++) {
            f(i);
        }
    };
    return OffsetRange;
}());
var Position = /** @class */ (function () {
    function Position(lineNumber, column) {
        this.lineNumber = lineNumber;
        this.column = column;
    }
    Position.prototype.equals = function (other) {
        return Position.equals(this, other);
    };
    Position.equals = function (a, b) {
        if (!a && !b) {
            return true;
        }
        return !!a && !!b && a.lineNumber === b.lineNumber && a.column === b.column;
    };
    Position.prototype.isBefore = function (other) {
        return Position.isBefore(this, other);
    };
    Position.isBefore = function (a, b) {
        if (a.lineNumber < b.lineNumber) {
            return true;
        }
        if (b.lineNumber < a.lineNumber) {
            return false;
        }
        return a.column < b.column;
    };
    Position.prototype.isBeforeOrEqual = function (other) {
        return Position.isBeforeOrEqual(this, other);
    };
    Position.isBeforeOrEqual = function (a, b) {
        if (a.lineNumber < b.lineNumber) {
            return true;
        }
        if (b.lineNumber < a.lineNumber) {
            return false;
        }
        return a.column <= b.column;
    };
    return Position;
}());
var Range = /** @class */ (function () {
    function Range(startLineNumber, startColumn, endLineNumber, endColumn) {
        if (startLineNumber > endLineNumber || startLineNumber === endLineNumber && startColumn > endColumn) {
            this.startLineNumber = endLineNumber;
            this.startColumn = endColumn;
            this.endLineNumber = startLineNumber;
            this.endColumn = startColumn;
        }
        else {
            this.startLineNumber = startLineNumber;
            this.startColumn = startColumn;
            this.endLineNumber = endLineNumber;
            this.endColumn = endColumn;
        }
    }
    Range.prototype.isEmpty = function () {
        return Range.isEmpty(this);
    };
    Range.isEmpty = function (range) {
        return range.startLineNumber === range.endLineNumber && range.startColumn === range.endColumn;
    };
    Range.prototype.containsPosition = function (position) {
        return Range.containsPosition(this, position);
    };
    Range.containsPosition = function (range, position) {
        if (position.lineNumber < range.startLineNumber || position.lineNumber > range.endLineNumber) {
            return false;
        }
        if (position.lineNumber === range.startLineNumber && position.column < range.startColumn) {
            return false;
        }
        if (position.lineNumber === range.endLineNumber && position.column > range.endColumn) {
            return false;
        }
        return true;
    };
    Range.prototype.containsRange = function (range) {
        return Range.containsRange(this, range);
    };
    Range.containsRange = function (range, otherRange) {
        if (otherRange.startLineNumber < range.startLineNumber || otherRange.endLineNumber < range.startLineNumber) {
            return false;
        }
        if (otherRange.startLineNumber > range.endLineNumber || otherRange.endLineNumber > range.endLineNumber) {
            return false;
        }
        if (otherRange.startLineNumber === range.startLineNumber && otherRange.startColumn < range.startColumn) {
            return false;
        }
        if (otherRange.endLineNumber === range.endLineNumber && otherRange.endColumn > range.endColumn) {
            return false;
        }
        return true;
    };
    Range.prototype.strictContainsRange = function (range) {
        return Range.strictContainsRange(this, range);
    };
    Range.strictContainsRange = function (range, otherRange) {
        if (otherRange.startLineNumber < range.startLineNumber || otherRange.endLineNumber < range.startLineNumber) {
            return false;
        }
        if (otherRange.startLineNumber > range.endLineNumber || otherRange.endLineNumber > range.endLineNumber) {
            return false;
        }
        if (otherRange.startLineNumber === range.startLineNumber && otherRange.startColumn <= range.startColumn) {
            return false;
        }
        if (otherRange.endLineNumber === range.endLineNumber && otherRange.endColumn >= range.endColumn) {
            return false;
        }
        return true;
    };
    Range.prototype.plusRange = function (range) {
        return Range.plusRange(this, range);
    };
    Range.plusRange = function (a, b) {
        var startLineNumber;
        var startColumn;
        var endLineNumber;
        var endColumn;
        if (b.startLineNumber < a.startLineNumber) {
            startLineNumber = b.startLineNumber;
            startColumn = b.startColumn;
        }
        else if (b.startLineNumber === a.startLineNumber) {
            startLineNumber = b.startLineNumber;
            startColumn = Math.min(b.startColumn, a.startColumn);
        }
        else {
            startLineNumber = a.startLineNumber;
            startColumn = a.startColumn;
        }
        if (b.endLineNumber > a.endLineNumber) {
            endLineNumber = b.endLineNumber;
            endColumn = b.endColumn;
        }
        else if (b.endLineNumber === a.endLineNumber) {
            endLineNumber = b.endLineNumber;
            endColumn = Math.max(b.endColumn, a.endColumn);
        }
        else {
            endLineNumber = a.endLineNumber;
            endColumn = a.endColumn;
        }
        return new Range(startLineNumber, startColumn, endLineNumber, endColumn);
    };
    Range.prototype.intersectRanges = function (range) {
        return Range.intersectRanges(this, range);
    };
    Range.intersectRanges = function (a, b) {
        var resultStartLineNumber = a.startLineNumber;
        var resultStartColumn = a.startColumn;
        var resultEndLineNumber = a.endLineNumber;
        var resultEndColumn = a.endColumn;
        var otherStartLineNumber = b.startLineNumber;
        var otherStartColumn = b.startColumn;
        var otherEndLineNumber = b.endLineNumber;
        var otherEndColumn = b.endColumn;
        if (resultStartLineNumber < otherStartLineNumber) {
            resultStartLineNumber = otherStartLineNumber;
            resultStartColumn = otherStartColumn;
        }
        else if (resultStartLineNumber === otherStartLineNumber) {
            resultStartColumn = Math.max(resultStartColumn, otherStartColumn);
        }
        if (resultEndLineNumber > otherEndLineNumber) {
            resultEndLineNumber = otherEndLineNumber;
            resultEndColumn = otherEndColumn;
        }
        else if (resultEndLineNumber === otherEndLineNumber) {
            resultEndColumn = Math.min(resultEndColumn, otherEndColumn);
        }
        if (resultStartLineNumber > resultEndLineNumber) {
            return null;
        }
        if (resultStartLineNumber === resultEndLineNumber && resultStartColumn > resultEndColumn) {
            return null;
        }
        return new Range(resultStartLineNumber, resultStartColumn, resultEndLineNumber, resultEndColumn);
    };
    Range.prototype.equalsRange = function (other) {
        return Range.equalsRange(this, other);
    };
    Range.equalsRange = function (a, b) {
        if (!a && !b) {
            return true;
        }
        return !!a && !!b && a.startLineNumber === b.startLineNumber && a.startColumn === b.startColumn && a.endLineNumber === b.endLineNumber && a.endColumn === b.endColumn;
    };
    Range.prototype.getEndPosition = function () {
        return Range.getEndPosition(this);
    };
    Range.getEndPosition = function (range) {
        return new Position(range.endLineNumber, range.endColumn);
    };
    Range.prototype.getStartPosition = function () {
        return Range.getStartPosition(this);
    };
    Range.getStartPosition = function (range) {
        return new Position(range.startLineNumber, range.startColumn);
    };
    Range.prototype.collapseToStart = function () {
        return Range.collapseToStart(this);
    };
    Range.collapseToStart = function (range) {
        return new Range(range.startLineNumber, range.startColumn, range.startLineNumber, range.startColumn);
    };
    Range.prototype.collapseToEnd = function () {
        return Range.collapseToEnd(this);
    };
    Range.collapseToEnd = function (range) {
        return new Range(range.endLineNumber, range.endColumn, range.endLineNumber, range.endColumn);
    };
    Range.fromPositions = function (start, end) {
        if (end === void 0) { end = start; }
        return new Range(start.lineNumber, start.column, end.lineNumber, end.column);
    };
    return Range;
}());
function findLastMonotonous(array, predicate) {
    var idx = findLastIdxMonotonous(array, predicate);
    return idx === -1 ? undefined : array[idx];
}
function findLastIdxMonotonous(array, predicate, startIdx, endIdxEx) {
    if (startIdx === void 0) { startIdx = 0; }
    if (endIdxEx === void 0) { endIdxEx = array.length; }
    var i = startIdx;
    var j = endIdxEx;
    while (i < j) {
        var k = Math.floor((i + j) / 2);
        if (predicate(array[k])) {
            i = k + 1;
        }
        else {
            j = k;
        }
    }
    return i - 1;
}
function findFirstMonotonous(array, predicate) {
    var idx = findFirstIdxMonotonousOrArrLen(array, predicate);
    return idx === array.length ? undefined : array[idx];
}
function findFirstIdxMonotonousOrArrLen(array, predicate, startIdx, endIdxEx) {
    if (startIdx === void 0) { startIdx = 0; }
    if (endIdxEx === void 0) { endIdxEx = array.length; }
    var i = startIdx;
    var j = endIdxEx;
    while (i < j) {
        var k = Math.floor((i + j) / 2);
        if (predicate(array[k])) {
            j = k;
        }
        else {
            i = k + 1;
        }
    }
    return i;
}
var MonotonousArray = /** @class */ (function () {
    function MonotonousArray(_array) {
        this._array = _array;
        this._findLastMonotonousLastIdx = 0;
    }
    MonotonousArray.prototype.findLastMonotonous = function (predicate) {
        var e_3, _a;
        if (_b.assertInvariants) {
            if (this._prevFindLastPredicate) {
                try {
                    for (var _g = __values(this._array), _h = _g.next(); !_h.done; _h = _g.next()) {
                        var item = _h.value;
                        if (this._prevFindLastPredicate(item) && !predicate(item)) {
                            throw new Error("MonotonousArray: current predicate must be weaker than (or equal to) the previous predicate.");
                        }
                    }
                }
                catch (e_3_1) { e_3 = { error: e_3_1 }; }
                finally {
                    try {
                        if (_h && !_h.done && (_a = _g.return)) _a.call(_g);
                    }
                    finally { if (e_3) throw e_3.error; }
                }
            }
            this._prevFindLastPredicate = predicate;
        }
        var idx = findLastIdxMonotonous(this._array, predicate, this._findLastMonotonousLastIdx);
        this._findLastMonotonousLastIdx = idx + 1;
        return idx === -1 ? undefined : this._array[idx];
    };
    return MonotonousArray;
}());
_b = MonotonousArray;
(function () {
    _b.assertInvariants = false;
})();
var LineRange = /** @class */ (function () {
    function LineRange(startLineNumber, endLineNumberExclusive) {
        if (startLineNumber > endLineNumberExclusive) {
            throw new BugIndicatingError("startLineNumber ".concat(startLineNumber, " cannot be after endLineNumberExclusive ").concat(endLineNumberExclusive));
        }
        this.startLineNumber = startLineNumber;
        this.endLineNumberExclusive = endLineNumberExclusive;
    }
    LineRange.fromRangeInclusive = function (range) {
        return new LineRange(range.startLineNumber, range.endLineNumber + 1);
    };
    LineRange.join = function (lineRanges) {
        if (lineRanges.length === 0) {
            throw new BugIndicatingError("lineRanges cannot be empty");
        }
        var startLineNumber = lineRanges[0].startLineNumber;
        var endLineNumberExclusive = lineRanges[0].endLineNumberExclusive;
        for (var i = 1; i < lineRanges.length; i++) {
            startLineNumber = Math.min(startLineNumber, lineRanges[i].startLineNumber);
            endLineNumberExclusive = Math.max(endLineNumberExclusive, lineRanges[i].endLineNumberExclusive);
        }
        return new LineRange(startLineNumber, endLineNumberExclusive);
    };
    LineRange.ofLength = function (startLineNumber, length) {
        return new LineRange(startLineNumber, startLineNumber + length);
    };
    Object.defineProperty(LineRange.prototype, "isEmpty", {
        get: function () {
            return this.startLineNumber === this.endLineNumberExclusive;
        },
        enumerable: false,
        configurable: true
    });
    LineRange.prototype.delta = function (offset) {
        return new LineRange(this.startLineNumber + offset, this.endLineNumberExclusive + offset);
    };
    Object.defineProperty(LineRange.prototype, "length", {
        get: function () {
            return this.endLineNumberExclusive - this.startLineNumber;
        },
        enumerable: false,
        configurable: true
    });
    LineRange.prototype.join = function (other) {
        return new LineRange(Math.min(this.startLineNumber, other.startLineNumber), Math.max(this.endLineNumberExclusive, other.endLineNumberExclusive));
    };
    LineRange.prototype.intersect = function (other) {
        var startLineNumber = Math.max(this.startLineNumber, other.startLineNumber);
        var endLineNumberExclusive = Math.min(this.endLineNumberExclusive, other.endLineNumberExclusive);
        if (startLineNumber <= endLineNumberExclusive) {
            return new LineRange(startLineNumber, endLineNumberExclusive);
        }
        return undefined;
    };
    LineRange.prototype.overlapOrTouch = function (other) {
        return this.startLineNumber <= other.endLineNumberExclusive && other.startLineNumber <= this.endLineNumberExclusive;
    };
    LineRange.prototype.toInclusiveRange = function () {
        if (this.isEmpty) {
            return null;
        }
        return new Range(this.startLineNumber, 1, this.endLineNumberExclusive - 1, Number.MAX_SAFE_INTEGER);
    };
    LineRange.prototype.toOffsetRange = function () {
        return new OffsetRange(this.startLineNumber - 1, this.endLineNumberExclusive - 1);
    };
    return LineRange;
}());
var LineRangeSet = /** @class */ (function () {
    function LineRangeSet(_normalizedRanges) {
        if (_normalizedRanges === void 0) { _normalizedRanges = []; }
        this._normalizedRanges = _normalizedRanges;
    }
    Object.defineProperty(LineRangeSet.prototype, "ranges", {
        get: function () {
            return this._normalizedRanges;
        },
        enumerable: false,
        configurable: true
    });
    LineRangeSet.prototype.addRange = function (range) {
        if (range.length === 0) {
            return;
        }
        var joinRangeStartIdx = findFirstIdxMonotonousOrArrLen(this._normalizedRanges, function (r) { return r.endLineNumberExclusive >= range.startLineNumber; });
        var joinRangeEndIdxExclusive = findLastIdxMonotonous(this._normalizedRanges, function (r) { return r.startLineNumber <= range.endLineNumberExclusive; }) + 1;
        if (joinRangeStartIdx === joinRangeEndIdxExclusive) {
            this._normalizedRanges.splice(joinRangeStartIdx, 0, range);
        }
        else if (joinRangeStartIdx === joinRangeEndIdxExclusive - 1) {
            var joinRange = this._normalizedRanges[joinRangeStartIdx];
            this._normalizedRanges[joinRangeStartIdx] = joinRange.join(range);
        }
        else {
            var joinRange = this._normalizedRanges[joinRangeStartIdx].join(this._normalizedRanges[joinRangeEndIdxExclusive - 1]).join(range);
            this._normalizedRanges.splice(joinRangeStartIdx, joinRangeEndIdxExclusive - joinRangeStartIdx, joinRange);
        }
    };
    LineRangeSet.prototype.contains = function (lineNumber) {
        var rangeThatStartsBeforeEnd = findLastMonotonous(this._normalizedRanges, function (r) { return r.startLineNumber <= lineNumber; });
        return !!rangeThatStartsBeforeEnd && rangeThatStartsBeforeEnd.endLineNumberExclusive > lineNumber;
    };
    LineRangeSet.prototype.subtractFrom = function (range) {
        var joinRangeStartIdx = findFirstIdxMonotonousOrArrLen(this._normalizedRanges, function (r) { return r.endLineNumberExclusive >= range.startLineNumber; });
        var joinRangeEndIdxExclusive = findLastIdxMonotonous(this._normalizedRanges, function (r) { return r.startLineNumber <= range.endLineNumberExclusive; }) + 1;
        if (joinRangeStartIdx === joinRangeEndIdxExclusive) {
            return new LineRangeSet([range]);
        }
        var result = [];
        var startLineNumber = range.startLineNumber;
        for (var i = joinRangeStartIdx; i < joinRangeEndIdxExclusive; i++) {
            var r = this._normalizedRanges[i];
            if (r.startLineNumber > startLineNumber) {
                result.push(new LineRange(startLineNumber, r.startLineNumber));
            }
            startLineNumber = r.endLineNumberExclusive;
        }
        if (startLineNumber < range.endLineNumberExclusive) {
            result.push(new LineRange(startLineNumber, range.endLineNumberExclusive));
        }
        return new LineRangeSet(result);
    };
    LineRangeSet.prototype.getIntersection = function (other) {
        var result = [];
        var i1 = 0;
        var i2 = 0;
        while (i1 < this._normalizedRanges.length && i2 < other._normalizedRanges.length) {
            var r1 = this._normalizedRanges[i1];
            var r2 = other._normalizedRanges[i2];
            var i = r1.intersect(r2);
            if (i && !i.isEmpty) {
                result.push(i);
            }
            if (r1.endLineNumberExclusive < r2.endLineNumberExclusive) {
                i1++;
            }
            else {
                i2++;
            }
        }
        return new LineRangeSet(result);
    };
    LineRangeSet.prototype.getWithDelta = function (value) {
        return new LineRangeSet(this._normalizedRanges.map(function (r) { return r.delta(value); }));
    };
    return LineRangeSet;
}());
var TextLength = /** @class */ (function () {
    function TextLength(lineCount, columnCount) {
        this.lineCount = lineCount;
        this.columnCount = columnCount;
    }
    TextLength.prototype.toLineRange = function () {
        return LineRange.ofLength(1, this.lineCount);
    };
    TextLength.prototype.addToPosition = function (position) {
        if (this.lineCount === 0) {
            return new Position(position.lineNumber, position.column + this.columnCount);
        }
        else {
            return new Position(position.lineNumber + this.lineCount, this.columnCount + 1);
        }
    };
    return TextLength;
}());
_c = TextLength;
(function () {
    _c.zero = new _c(0, 0);
})();
var LineBasedText = /** @class */ (function () {
    function LineBasedText(_getLineContent, _lineCount) {
        assert(_lineCount >= 1);
        this._getLineContent = _getLineContent;
        this._lineCount = _lineCount;
    }
    LineBasedText.prototype.getValueOfRange = function (range) {
        if (range.startLineNumber === range.endLineNumber) {
            return this._getLineContent(range.startLineNumber).substring(range.startColumn - 1, range.endColumn - 1);
        }
        var result = this._getLineContent(range.startLineNumber).substring(range.startColumn - 1);
        for (var i = range.startLineNumber + 1; i < range.endLineNumber; i++) {
            result += "\n" + this._getLineContent(i);
        }
        result += "\n" + this._getLineContent(range.endLineNumber).substring(0, range.endColumn - 1);
        return result;
    };
    LineBasedText.prototype.getLineLength = function (lineNumber) {
        return this._getLineContent(lineNumber).length;
    };
    Object.defineProperty(LineBasedText.prototype, "length", {
        get: function () {
            var lastLine = this._getLineContent(this._lineCount);
            return new TextLength(this._lineCount - 1, lastLine.length);
        },
        enumerable: false,
        configurable: true
    });
    return LineBasedText;
}());
var ArrayText = /** @class */ (function (_super) {
    __extends(ArrayText, _super);
    function ArrayText(lines) {
        return _super.call(this, function (lineNumber) { return lines[lineNumber - 1]; }, lines.length) || this;
    }
    return ArrayText;
}(LineBasedText));
var LinesDiff = /** @class */ (function () {
    function LinesDiff(changes, moves, hitTimeout) {
        this.changes = changes;
        this.moves = moves;
        this.hitTimeout = hitTimeout;
    }
    return LinesDiff;
}());
var MovedText = /** @class */ (function () {
    function MovedText(lineRangeMapping, changes) {
        this.lineRangeMapping = lineRangeMapping;
        this.changes = changes;
    }
    return MovedText;
}());
var LineRangeMapping = /** @class */ (function () {
    function LineRangeMapping(originalRange, modifiedRange) {
        this.original = originalRange;
        this.modified = modifiedRange;
    }
    LineRangeMapping.prototype.join = function (other) {
        return new LineRangeMapping(this.original.join(other.original), this.modified.join(other.modified));
    };
    Object.defineProperty(LineRangeMapping.prototype, "changedLineCount", {
        get: function () {
            return Math.max(this.original.length, this.modified.length);
        },
        enumerable: false,
        configurable: true
    });
    LineRangeMapping.prototype.toRangeMapping = function () {
        var origInclusiveRange = this.original.toInclusiveRange();
        var modInclusiveRange = this.modified.toInclusiveRange();
        if (origInclusiveRange && modInclusiveRange) {
            return new RangeMapping(origInclusiveRange, modInclusiveRange);
        }
        else if (this.original.startLineNumber === 1 || this.modified.startLineNumber === 1) {
            if (!(this.modified.startLineNumber === 1 && this.original.startLineNumber === 1)) {
                throw new BugIndicatingError("not a valid diff");
            }
            return new RangeMapping(new Range(this.original.startLineNumber, 1, this.original.endLineNumberExclusive, 1), new Range(this.modified.startLineNumber, 1, this.modified.endLineNumberExclusive, 1));
        }
        else {
            return new RangeMapping(new Range(this.original.startLineNumber - 1, Number.MAX_SAFE_INTEGER, this.original.endLineNumberExclusive - 1, Number.MAX_SAFE_INTEGER), new Range(this.modified.startLineNumber - 1, Number.MAX_SAFE_INTEGER, this.modified.endLineNumberExclusive - 1, Number.MAX_SAFE_INTEGER));
        }
    };
    LineRangeMapping.prototype.toRangeMapping2 = function (original, modified) {
        if (isValidLineNumber(this.original.endLineNumberExclusive, original) && isValidLineNumber(this.modified.endLineNumberExclusive, modified)) {
            return new RangeMapping(new Range(this.original.startLineNumber, 1, this.original.endLineNumberExclusive, 1), new Range(this.modified.startLineNumber, 1, this.modified.endLineNumberExclusive, 1));
        }
        if (!this.original.isEmpty && !this.modified.isEmpty) {
            return new RangeMapping(Range.fromPositions(new Position(this.original.startLineNumber, 1), normalizePosition(new Position(this.original.endLineNumberExclusive - 1, Number.MAX_SAFE_INTEGER), original)), Range.fromPositions(new Position(this.modified.startLineNumber, 1), normalizePosition(new Position(this.modified.endLineNumberExclusive - 1, Number.MAX_SAFE_INTEGER), modified)));
        }
        if (this.original.startLineNumber > 1 && this.modified.startLineNumber > 1) {
            return new RangeMapping(Range.fromPositions(normalizePosition(new Position(this.original.startLineNumber - 1, Number.MAX_SAFE_INTEGER), original), normalizePosition(new Position(this.original.endLineNumberExclusive - 1, Number.MAX_SAFE_INTEGER), original)), Range.fromPositions(normalizePosition(new Position(this.modified.startLineNumber - 1, Number.MAX_SAFE_INTEGER), modified), normalizePosition(new Position(this.modified.endLineNumberExclusive - 1, Number.MAX_SAFE_INTEGER), modified)));
        }
        throw new BugIndicatingError();
    };
    return LineRangeMapping;
}());
function normalizePosition(position, content) {
    if (position.lineNumber < 1) {
        return new Position(1, 1);
    }
    if (position.lineNumber > content.length) {
        return new Position(content.length, content[content.length - 1].length + 1);
    }
    var line = content[position.lineNumber - 1];
    if (position.column > line.length + 1) {
        return new Position(position.lineNumber, line.length + 1);
    }
    return position;
}
function isValidLineNumber(lineNumber, lines) {
    return lineNumber >= 1 && lineNumber <= lines.length;
}
var DetailedLineRangeMapping = /** @class */ (function (_super) {
    __extends(DetailedLineRangeMapping, _super);
    function DetailedLineRangeMapping(originalRange, modifiedRange, innerChanges) {
        var _this = _super.call(this, originalRange, modifiedRange) || this;
        _this.innerChanges = innerChanges;
        return _this;
    }
    DetailedLineRangeMapping.fromRangeMappings = function (rangeMappings) {
        var originalRange = LineRange.join(rangeMappings.map(function (r) { return LineRange.fromRangeInclusive(r.originalRange); }));
        var modifiedRange = LineRange.join(rangeMappings.map(function (r) { return LineRange.fromRangeInclusive(r.modifiedRange); }));
        return new DetailedLineRangeMapping(originalRange, modifiedRange, rangeMappings);
    };
    DetailedLineRangeMapping.prototype.flip = function () {
        var _a;
        return new DetailedLineRangeMapping(this.modified, this.original, (_a = this.innerChanges) === null || _a === void 0 ? void 0 : _a.map(function (c) { return c.flip(); }));
    };
    DetailedLineRangeMapping.prototype.withInnerChangesFromLineRanges = function () {
        return new DetailedLineRangeMapping(this.original, this.modified, [this.toRangeMapping()]);
    };
    return DetailedLineRangeMapping;
}(LineRangeMapping));
var RangeMapping = /** @class */ (function () {
    function RangeMapping(originalRange, modifiedRange) {
        this.originalRange = originalRange;
        this.modifiedRange = modifiedRange;
    }
    RangeMapping.join = function (rangeMappings) {
        if (rangeMappings.length === 0) {
            throw new BugIndicatingError("Cannot join an empty list of range mappings");
        }
        var result = rangeMappings[0];
        for (var i = 1; i < rangeMappings.length; i++) {
            result = result.join(rangeMappings[i]);
        }
        return result;
    };
    RangeMapping.assertSorted = function (rangeMappings) {
        for (var i = 1; i < rangeMappings.length; i++) {
            var previous = rangeMappings[i - 1];
            var current = rangeMappings[i];
            if (!(previous.originalRange.getEndPosition().isBeforeOrEqual(current.originalRange.getStartPosition()) && previous.modifiedRange.getEndPosition().isBeforeOrEqual(current.modifiedRange.getStartPosition()))) {
                throw new BugIndicatingError("Range mappings must be sorted");
            }
        }
    };
    RangeMapping.prototype.flip = function () {
        return new RangeMapping(this.modifiedRange, this.originalRange);
    };
    RangeMapping.prototype.join = function (other) {
        return new RangeMapping(this.originalRange.plusRange(other.originalRange), this.modifiedRange.plusRange(other.modifiedRange));
    };
    return RangeMapping;
}());
function lineRangeMappingFromRangeMappings(alignments, originalLines, modifiedLines, dontAssertStartLine) {
    var e_4, _a;
    if (dontAssertStartLine === void 0) { dontAssertStartLine = false; }
    var changes = [];
    try {
        for (var _g = __values(groupAdjacentBy(alignments.map(function (a) { return getLineRangeMapping(a, originalLines, modifiedLines); }), function (a1, a2) { return a1.original.overlapOrTouch(a2.original) || a1.modified.overlapOrTouch(a2.modified); })), _h = _g.next(); !_h.done; _h = _g.next()) {
            var g = _h.value;
            var first = g[0];
            var last = g[g.length - 1];
            changes.push(new DetailedLineRangeMapping(first.original.join(last.original), first.modified.join(last.modified), g.map(function (a) { return a.innerChanges[0]; })));
        }
    }
    catch (e_4_1) { e_4 = { error: e_4_1 }; }
    finally {
        try {
            if (_h && !_h.done && (_a = _g.return)) _a.call(_g);
        }
        finally { if (e_4) throw e_4.error; }
    }
    assertFn(function () {
        if (!dontAssertStartLine && changes.length > 0) {
            if (changes[0].modified.startLineNumber !== changes[0].original.startLineNumber) {
                return false;
            }
            if (modifiedLines.length.lineCount - changes[changes.length - 1].modified.endLineNumberExclusive !== originalLines.length.lineCount - changes[changes.length - 1].original.endLineNumberExclusive) {
                return false;
            }
        }
        return checkAdjacentItems(changes, function (m1, m2) { return m2.original.startLineNumber - m1.original.endLineNumberExclusive === m2.modified.startLineNumber - m1.modified.endLineNumberExclusive && // There has to be an unchanged line in between (otherwise both diffs should have been joined)
            m1.original.endLineNumberExclusive < m2.original.startLineNumber && m1.modified.endLineNumberExclusive < m2.modified.startLineNumber; });
    });
    return changes;
}
function getLineRangeMapping(rangeMapping, originalLines, modifiedLines) {
    var lineStartDelta = 0;
    var lineEndDelta = 0;
    if (rangeMapping.modifiedRange.endColumn === 1 && rangeMapping.originalRange.endColumn === 1 && rangeMapping.originalRange.startLineNumber + lineStartDelta <= rangeMapping.originalRange.endLineNumber && rangeMapping.modifiedRange.startLineNumber + lineStartDelta <= rangeMapping.modifiedRange.endLineNumber) {
        lineEndDelta = -1;
    }
    if (rangeMapping.modifiedRange.startColumn - 1 >= modifiedLines.getLineLength(rangeMapping.modifiedRange.startLineNumber) && rangeMapping.originalRange.startColumn - 1 >= originalLines.getLineLength(rangeMapping.originalRange.startLineNumber) && rangeMapping.originalRange.startLineNumber <= rangeMapping.originalRange.endLineNumber + lineEndDelta && rangeMapping.modifiedRange.startLineNumber <= rangeMapping.modifiedRange.endLineNumber + lineEndDelta) {
        lineStartDelta = 1;
    }
    var originalLineRange = new LineRange(rangeMapping.originalRange.startLineNumber + lineStartDelta, rangeMapping.originalRange.endLineNumber + 1 + lineEndDelta);
    var modifiedLineRange = new LineRange(rangeMapping.modifiedRange.startLineNumber + lineStartDelta, rangeMapping.modifiedRange.endLineNumber + 1 + lineEndDelta);
    return new DetailedLineRangeMapping(originalLineRange, modifiedLineRange, [rangeMapping]);
}
var DiffAlgorithmResult = /** @class */ (function () {
    function DiffAlgorithmResult(diffs, hitTimeout) {
        this.diffs = diffs;
        this.hitTimeout = hitTimeout;
    }
    DiffAlgorithmResult.trivial = function (seq1, seq2) {
        return new DiffAlgorithmResult([new SequenceDiff(OffsetRange.ofLength(seq1.length), OffsetRange.ofLength(seq2.length))], false);
    };
    DiffAlgorithmResult.trivialTimedOut = function (seq1, seq2) {
        return new DiffAlgorithmResult([new SequenceDiff(OffsetRange.ofLength(seq1.length), OffsetRange.ofLength(seq2.length))], true);
    };
    return DiffAlgorithmResult;
}());
var SequenceDiff = /** @class */ (function () {
    function SequenceDiff(seq1Range, seq2Range) {
        this.seq1Range = seq1Range;
        this.seq2Range = seq2Range;
    }
    SequenceDiff.invert = function (sequenceDiffs, doc1Length) {
        var result = [];
        forEachAdjacent(sequenceDiffs, function (a, b) {
            result.push(SequenceDiff.fromOffsetPairs(a ? a.getEndExclusives() : OffsetPair.zero, b ? b.getStarts() : new OffsetPair(doc1Length, (a ? a.seq2Range.endExclusive - a.seq1Range.endExclusive : 0) + doc1Length)));
        });
        return result;
    };
    SequenceDiff.fromOffsetPairs = function (start, endExclusive) {
        return new SequenceDiff(new OffsetRange(start.offset1, endExclusive.offset1), new OffsetRange(start.offset2, endExclusive.offset2));
    };
    SequenceDiff.assertSorted = function (sequenceDiffs) {
        var e_5, _a;
        var last = undefined;
        try {
            for (var sequenceDiffs_1 = __values(sequenceDiffs), sequenceDiffs_1_1 = sequenceDiffs_1.next(); !sequenceDiffs_1_1.done; sequenceDiffs_1_1 = sequenceDiffs_1.next()) {
                var cur = sequenceDiffs_1_1.value;
                if (last) {
                    if (!(last.seq1Range.endExclusive <= cur.seq1Range.start && last.seq2Range.endExclusive <= cur.seq2Range.start)) {
                        throw new BugIndicatingError("Sequence diffs must be sorted");
                    }
                }
                last = cur;
            }
        }
        catch (e_5_1) { e_5 = { error: e_5_1 }; }
        finally {
            try {
                if (sequenceDiffs_1_1 && !sequenceDiffs_1_1.done && (_a = sequenceDiffs_1.return)) _a.call(sequenceDiffs_1);
            }
            finally { if (e_5) throw e_5.error; }
        }
    };
    SequenceDiff.prototype.swap = function () {
        return new SequenceDiff(this.seq2Range, this.seq1Range);
    };
    SequenceDiff.prototype.join = function (other) {
        return new SequenceDiff(this.seq1Range.join(other.seq1Range), this.seq2Range.join(other.seq2Range));
    };
    SequenceDiff.prototype.delta = function (offset) {
        if (offset === 0) {
            return this;
        }
        return new SequenceDiff(this.seq1Range.delta(offset), this.seq2Range.delta(offset));
    };
    SequenceDiff.prototype.deltaStart = function (offset) {
        if (offset === 0) {
            return this;
        }
        return new SequenceDiff(this.seq1Range.deltaStart(offset), this.seq2Range.deltaStart(offset));
    };
    SequenceDiff.prototype.deltaEnd = function (offset) {
        if (offset === 0) {
            return this;
        }
        return new SequenceDiff(this.seq1Range.deltaEnd(offset), this.seq2Range.deltaEnd(offset));
    };
    SequenceDiff.prototype.intersect = function (other) {
        var i1 = this.seq1Range.intersect(other.seq1Range);
        var i2 = this.seq2Range.intersect(other.seq2Range);
        if (!i1 || !i2) {
            return undefined;
        }
        return new SequenceDiff(i1, i2);
    };
    SequenceDiff.prototype.getStarts = function () {
        return new OffsetPair(this.seq1Range.start, this.seq2Range.start);
    };
    SequenceDiff.prototype.getEndExclusives = function () {
        return new OffsetPair(this.seq1Range.endExclusive, this.seq2Range.endExclusive);
    };
    return SequenceDiff;
}());
var OffsetPair = /** @class */ (function () {
    function OffsetPair(offset1, offset2) {
        this.offset1 = offset1;
        this.offset2 = offset2;
    }
    OffsetPair.prototype.delta = function (offset) {
        if (offset === 0) {
            return this;
        }
        return new _d(this.offset1 + offset, this.offset2 + offset);
    };
    OffsetPair.prototype.equals = function (other) {
        return this.offset1 === other.offset1 && this.offset2 === other.offset2;
    };
    return OffsetPair;
}());
_d = OffsetPair;
(function () {
    _d.zero = new _d(0, 0);
})();
(function () {
    _d.max = new _d(Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER);
})();
var InfiniteTimeout = /** @class */ (function () {
    function InfiniteTimeout() {
    }
    InfiniteTimeout.prototype.isValid = function () {
        return true;
    };
    return InfiniteTimeout;
}());
_e = InfiniteTimeout;
(function () {
    _e.instance = new _e();
})();
var DateTimeout = /** @class */ (function () {
    function DateTimeout(timeout) {
        this.timeout = timeout;
        this.startTime = Date.now();
        this.valid = true;
        if (timeout <= 0) {
            throw new BugIndicatingError("timeout must be positive");
        }
    }
    DateTimeout.prototype.isValid = function () {
        var valid = Date.now() - this.startTime < this.timeout;
        if (!valid && this.valid) {
            this.valid = false;
        }
        return this.valid;
    };
    DateTimeout.prototype.disable = function () {
        this.timeout = Number.MAX_SAFE_INTEGER;
        this.isValid = function () { return true; };
        this.valid = true;
    };
    return DateTimeout;
}());
var Array2D = /** @class */ (function () {
    function Array2D(width, height) {
        this.width = width;
        this.height = height;
        this.array = [];
        this.array = new Array(width * height);
    }
    Array2D.prototype.get = function (x, y) {
        return this.array[x + y * this.width];
    };
    Array2D.prototype.set = function (x, y, value) {
        this.array[x + y * this.width] = value;
    };
    return Array2D;
}());
function isSpace(charCode) {
    return charCode === 32 || charCode === 9;
}
var LineRangeFragment = /** @class */ (function () {
    function LineRangeFragment(range, lines, source) {
        this.range = range;
        this.lines = lines;
        this.source = source;
        this.histogram = [];
        var counter = 0;
        for (var i = range.startLineNumber - 1; i < range.endLineNumberExclusive - 1; i++) {
            var line = lines[i];
            for (var j = 0; j < line.length; j++) {
                counter++;
                var chr = line[j];
                var key2 = _f.getKey(chr);
                this.histogram[key2] = (this.histogram[key2] || 0) + 1;
            }
            counter++;
            var key = _f.getKey("\n");
            this.histogram[key] = (this.histogram[key] || 0) + 1;
        }
        this.totalCount = counter;
    }
    LineRangeFragment.getKey = function (chr) {
        var key = this.chrKeys.get(chr);
        if (key === undefined) {
            key = this.chrKeys.size;
            this.chrKeys.set(chr, key);
        }
        return key;
    };
    LineRangeFragment.prototype.computeSimilarity = function (other) {
        var _a, _g;
        var sumDifferences = 0;
        var maxLength = Math.max(this.histogram.length, other.histogram.length);
        for (var i = 0; i < maxLength; i++) {
            sumDifferences += Math.abs(((_a = this.histogram[i]) !== null && _a !== void 0 ? _a : 0) - ((_g = other.histogram[i]) !== null && _g !== void 0 ? _g : 0));
        }
        return 1 - sumDifferences / (this.totalCount + other.totalCount);
    };
    return LineRangeFragment;
}());
_f = LineRangeFragment;
(function () {
    _f.chrKeys = /* @__PURE__ */ new Map();
})();
var DynamicProgrammingDiffing = /** @class */ (function () {
    function DynamicProgrammingDiffing() {
    }
    DynamicProgrammingDiffing.prototype.compute = function (sequence1, sequence2, timeout, equalityScore) {
        if (timeout === void 0) { timeout = InfiniteTimeout.instance; }
        if (sequence1.length === 0 || sequence2.length === 0) {
            return DiffAlgorithmResult.trivial(sequence1, sequence2);
        }
        var lcsLengths = new Array2D(sequence1.length, sequence2.length);
        var directions = new Array2D(sequence1.length, sequence2.length);
        var lengths = new Array2D(sequence1.length, sequence2.length);
        for (var s12 = 0; s12 < sequence1.length; s12++) {
            for (var s22 = 0; s22 < sequence2.length; s22++) {
                if (!timeout.isValid()) {
                    return DiffAlgorithmResult.trivialTimedOut(sequence1, sequence2);
                }
                var horizontalLen = s12 === 0 ? 0 : lcsLengths.get(s12 - 1, s22);
                var verticalLen = s22 === 0 ? 0 : lcsLengths.get(s12, s22 - 1);
                var extendedSeqScore = void 0;
                if (sequence1.getElement(s12) === sequence2.getElement(s22)) {
                    if (s12 === 0 || s22 === 0) {
                        extendedSeqScore = 0;
                    }
                    else {
                        extendedSeqScore = lcsLengths.get(s12 - 1, s22 - 1);
                    }
                    if (s12 > 0 && s22 > 0 && directions.get(s12 - 1, s22 - 1) === 3) {
                        extendedSeqScore += lengths.get(s12 - 1, s22 - 1);
                    }
                    extendedSeqScore += equalityScore ? equalityScore(s12, s22) : 1;
                }
                else {
                    extendedSeqScore = -1;
                }
                var newValue = Math.max(horizontalLen, verticalLen, extendedSeqScore);
                if (newValue === extendedSeqScore) {
                    var prevLen = s12 > 0 && s22 > 0 ? lengths.get(s12 - 1, s22 - 1) : 0;
                    lengths.set(s12, s22, prevLen + 1);
                    directions.set(s12, s22, 3);
                }
                else if (newValue === horizontalLen) {
                    lengths.set(s12, s22, 0);
                    directions.set(s12, s22, 1);
                }
                else if (newValue === verticalLen) {
                    lengths.set(s12, s22, 0);
                    directions.set(s12, s22, 2);
                }
                lcsLengths.set(s12, s22, newValue);
            }
        }
        var result = [];
        var lastAligningPosS1 = sequence1.length;
        var lastAligningPosS2 = sequence2.length;
        function reportDecreasingAligningPositions(s12, s22) {
            if (s12 + 1 !== lastAligningPosS1 || s22 + 1 !== lastAligningPosS2) {
                result.push(new SequenceDiff(new OffsetRange(s12 + 1, lastAligningPosS1), new OffsetRange(s22 + 1, lastAligningPosS2)));
            }
            lastAligningPosS1 = s12;
            lastAligningPosS2 = s22;
        }
        var s1 = sequence1.length - 1;
        var s2 = sequence2.length - 1;
        while (s1 >= 0 && s2 >= 0) {
            if (directions.get(s1, s2) === 3) {
                reportDecreasingAligningPositions(s1, s2);
                s1--;
                s2--;
            }
            else {
                if (directions.get(s1, s2) === 1) {
                    s1--;
                }
                else {
                    s2--;
                }
            }
        }
        reportDecreasingAligningPositions(-1, -1);
        result.reverse();
        return new DiffAlgorithmResult(result, false);
    };
    return DynamicProgrammingDiffing;
}());
var MyersDiffAlgorithm = /** @class */ (function () {
    function MyersDiffAlgorithm() {
    }
    MyersDiffAlgorithm.prototype.compute = function (seq1, seq2, timeout) {
        if (timeout === void 0) { timeout = InfiniteTimeout.instance; }
        if (seq1.length === 0 || seq2.length === 0) {
            return DiffAlgorithmResult.trivial(seq1, seq2);
        }
        var seqX = seq1;
        var seqY = seq2;
        function getXAfterSnake(x, y) {
            while (x < seqX.length && y < seqY.length && seqX.getElement(x) === seqY.getElement(y)) {
                x++;
                y++;
            }
            return x;
        }
        var d = 0;
        var V = new FastInt32Array();
        V.set(0, getXAfterSnake(0, 0));
        var paths = new FastArrayNegativeIndices();
        paths.set(0, V.get(0) === 0 ? null : new SnakePath(null, 0, 0, V.get(0)));
        var k = 0;
        loop: while (true) {
            d++;
            if (!timeout.isValid()) {
                return DiffAlgorithmResult.trivialTimedOut(seqX, seqY);
            }
            var lowerBound = -Math.min(d, seqY.length + d % 2);
            var upperBound = Math.min(d, seqX.length + d % 2);
            for (k = lowerBound; k <= upperBound; k += 2) {
                var maxXofDLineTop = k === upperBound ? -1 : V.get(k + 1);
                var maxXofDLineLeft = k === lowerBound ? -1 : V.get(k - 1) + 1;
                var x = Math.min(Math.max(maxXofDLineTop, maxXofDLineLeft), seqX.length);
                var y = x - k;
                if (x > seqX.length || y > seqY.length) {
                    continue;
                }
                var newMaxX = getXAfterSnake(x, y);
                V.set(k, newMaxX);
                var lastPath = x === maxXofDLineTop ? paths.get(k + 1) : paths.get(k - 1);
                paths.set(k, newMaxX !== x ? new SnakePath(lastPath, x, y, newMaxX - x) : lastPath);
                if (V.get(k) === seqX.length && V.get(k) - k === seqY.length) {
                    break loop;
                }
            }
        }
        var path = paths.get(k);
        var result = [];
        var lastAligningPosS1 = seqX.length;
        var lastAligningPosS2 = seqY.length;
        while (true) {
            var endX = path ? path.x + path.length : 0;
            var endY = path ? path.y + path.length : 0;
            if (endX !== lastAligningPosS1 || endY !== lastAligningPosS2) {
                result.push(new SequenceDiff(new OffsetRange(endX, lastAligningPosS1), new OffsetRange(endY, lastAligningPosS2)));
            }
            if (!path) {
                break;
            }
            lastAligningPosS1 = path.x;
            lastAligningPosS2 = path.y;
            path = path.prev;
        }
        result.reverse();
        return new DiffAlgorithmResult(result, false);
    };
    return MyersDiffAlgorithm;
}());
var SnakePath = /** @class */ (function () {
    function SnakePath(prev, x, y, length) {
        this.prev = prev;
        this.x = x;
        this.y = y;
        this.length = length;
    }
    return SnakePath;
}());
var FastInt32Array = /** @class */ (function () {
    function FastInt32Array() {
        this.positiveArr = new Int32Array(10);
        this.negativeArr = new Int32Array(10);
    }
    FastInt32Array.prototype.get = function (idx) {
        if (idx < 0) {
            idx = -idx - 1;
            return this.negativeArr[idx];
        }
        else {
            return this.positiveArr[idx];
        }
    };
    FastInt32Array.prototype.set = function (idx, value) {
        if (idx < 0) {
            idx = -idx - 1;
            if (idx >= this.negativeArr.length) {
                var arr = this.negativeArr;
                this.negativeArr = new Int32Array(arr.length * 2);
                this.negativeArr.set(arr);
            }
            this.negativeArr[idx] = value;
        }
        else {
            if (idx >= this.positiveArr.length) {
                var arr = this.positiveArr;
                this.positiveArr = new Int32Array(arr.length * 2);
                this.positiveArr.set(arr);
            }
            this.positiveArr[idx] = value;
        }
    };
    return FastInt32Array;
}());
var FastArrayNegativeIndices = /** @class */ (function () {
    function FastArrayNegativeIndices() {
        this.positiveArr = [];
        this.negativeArr = [];
    }
    FastArrayNegativeIndices.prototype.get = function (idx) {
        if (idx < 0) {
            idx = -idx - 1;
            return this.negativeArr[idx];
        }
        else {
            return this.positiveArr[idx];
        }
    };
    FastArrayNegativeIndices.prototype.set = function (idx, value) {
        if (idx < 0) {
            idx = -idx - 1;
            this.negativeArr[idx] = value;
        }
        else {
            this.positiveArr[idx] = value;
        }
    };
    return FastArrayNegativeIndices;
}());
var SetMap = /** @class */ (function () {
    function SetMap() {
        this.map = /* @__PURE__ */ new Map();
    }
    SetMap.prototype.add = function (key, value) {
        var values = this.map.get(key);
        if (!values) {
            values = /* @__PURE__ */ new Set();
            this.map.set(key, values);
        }
        values.add(value);
    };
    SetMap.prototype.forEach = function (key, fn) {
        var values = this.map.get(key);
        if (!values) {
            return;
        }
        values.forEach(fn);
    };
    SetMap.prototype.get = function (key) {
        var values = this.map.get(key);
        if (!values) {
            return /* @__PURE__ */ new Set();
        }
        return values;
    };
    return SetMap;
}());
var LinesSliceCharSequence = /** @class */ (function () {
    function LinesSliceCharSequence(lines, range, considerWhitespaceChanges) {
        this.lines = lines;
        this.range = range;
        this.considerWhitespaceChanges = considerWhitespaceChanges;
        this.elements = [];
        this.firstElementOffsetByLineIdx = [];
        this.lineStartOffsets = [];
        this.trimmedWsLengthsByLineIdx = [];
        this.firstElementOffsetByLineIdx.push(0);
        for (var lineNumber = this.range.startLineNumber; lineNumber <= this.range.endLineNumber; lineNumber++) {
            var line = lines[lineNumber - 1];
            var lineStartOffset = 0;
            if (lineNumber === this.range.startLineNumber && this.range.startColumn > 1) {
                lineStartOffset = this.range.startColumn - 1;
                line = line.substring(lineStartOffset);
            }
            this.lineStartOffsets.push(lineStartOffset);
            var trimmedWsLength = 0;
            if (!considerWhitespaceChanges) {
                var trimmedStartLine = line.trimStart();
                trimmedWsLength = line.length - trimmedStartLine.length;
                line = trimmedStartLine.trimEnd();
            }
            this.trimmedWsLengthsByLineIdx.push(trimmedWsLength);
            var lineLength = lineNumber === this.range.endLineNumber ? Math.min(this.range.endColumn - 1 - lineStartOffset - trimmedWsLength, line.length) : line.length;
            for (var i = 0; i < lineLength; i++) {
                this.elements.push(line.charCodeAt(i));
            }
            if (lineNumber < this.range.endLineNumber) {
                this.elements.push("\n".charCodeAt(0));
                this.firstElementOffsetByLineIdx.push(this.elements.length);
            }
        }
    }
    LinesSliceCharSequence.prototype.toString = function () {
        return "Slice: \"".concat(this.text, "\"");
    };
    Object.defineProperty(LinesSliceCharSequence.prototype, "text", {
        get: function () {
            return this.getText(new OffsetRange(0, this.length));
        },
        enumerable: false,
        configurable: true
    });
    LinesSliceCharSequence.prototype.getText = function (range) {
        return this.elements.slice(range.start, range.endExclusive).map(function (e) { return String.fromCharCode(e); }).join("");
    };
    LinesSliceCharSequence.prototype.getElement = function (offset) {
        return this.elements[offset];
    };
    Object.defineProperty(LinesSliceCharSequence.prototype, "length", {
        get: function () {
            return this.elements.length;
        },
        enumerable: false,
        configurable: true
    });
    LinesSliceCharSequence.prototype.getBoundaryScore = function (length) {
        var prevCategory = getCategory(length > 0 ? this.elements[length - 1] : -1);
        var nextCategory = getCategory(length < this.elements.length ? this.elements[length] : -1);
        if (prevCategory === 7 /* LineBreakCR */ && nextCategory === 8 /* LineBreakLF */) {
            return 0;
        }
        if (prevCategory === 8 /* LineBreakLF */) {
            return 150;
        }
        var score2 = 0;
        if (prevCategory !== nextCategory) {
            score2 += 10;
            if (prevCategory === 0 /* WordLower */ && nextCategory === 1 /* WordUpper */) {
                score2 += 1;
            }
        }
        score2 += getCategoryBoundaryScore(prevCategory);
        score2 += getCategoryBoundaryScore(nextCategory);
        return score2;
    };
    LinesSliceCharSequence.prototype.translateOffset = function (offset, preference) {
        if (preference === void 0) { preference = "right"; }
        var i = findLastIdxMonotonous(this.firstElementOffsetByLineIdx, function (value) { return value <= offset; });
        var lineOffset = offset - this.firstElementOffsetByLineIdx[i];
        return new Position(this.range.startLineNumber + i, 1 + this.lineStartOffsets[i] + lineOffset + (lineOffset === 0 && preference === "left" ? 0 : this.trimmedWsLengthsByLineIdx[i]));
    };
    LinesSliceCharSequence.prototype.translateRange = function (range) {
        var pos1 = this.translateOffset(range.start, "right");
        var pos2 = this.translateOffset(range.endExclusive, "left");
        if (pos2.isBefore(pos1)) {
            return Range.fromPositions(pos2, pos2);
        }
        return Range.fromPositions(pos1, pos2);
    };
    LinesSliceCharSequence.prototype.findWordContaining = function (offset) {
        if (offset < 0 || offset >= this.elements.length) {
            return undefined;
        }
        if (!isWordChar(this.elements[offset])) {
            return undefined;
        }
        var start = offset;
        while (start > 0 && isWordChar(this.elements[start - 1])) {
            start--;
        }
        var end = offset;
        while (end < this.elements.length && isWordChar(this.elements[end])) {
            end++;
        }
        return new OffsetRange(start, end);
    };
    LinesSliceCharSequence.prototype.findSubWordContaining = function (offset) {
        if (offset < 0 || offset >= this.elements.length) {
            return undefined;
        }
        if (!isWordChar(this.elements[offset])) {
            return undefined;
        }
        var start = offset;
        while (start > 0 && isWordChar(this.elements[start - 1]) && !isUpperCase(this.elements[start])) {
            start--;
        }
        var end = offset;
        while (end < this.elements.length && isWordChar(this.elements[end]) && !isUpperCase(this.elements[end])) {
            end++;
        }
        return new OffsetRange(start, end);
    };
    LinesSliceCharSequence.prototype.countLinesIn = function (range) {
        return this.translateOffset(range.endExclusive).lineNumber - this.translateOffset(range.start).lineNumber;
    };
    LinesSliceCharSequence.prototype.isStronglyEqual = function (offset1, offset2) {
        return this.elements[offset1] === this.elements[offset2];
    };
    LinesSliceCharSequence.prototype.extendToFullLines = function (range) {
        var _a, _g;
        var start = (_a = findLastMonotonous(this.firstElementOffsetByLineIdx, function (x) { return x <= range.start; })) !== null && _a !== void 0 ? _a : 0;
        var end = (_g = findFirstMonotonous(this.firstElementOffsetByLineIdx, function (x) { return range.endExclusive <= x; })) !== null && _g !== void 0 ? _g : this.elements.length;
        return new OffsetRange(start, end);
    };
    return LinesSliceCharSequence;
}());
function isWordChar(charCode) {
    return charCode >= 97 && charCode <= 122 || charCode >= 65 && charCode <= 90 || charCode >= 48 && charCode <= 57;
}
function isUpperCase(charCode) {
    return charCode >= 65 && charCode <= 90;
}
var score = (_a = {},
    _a[0 /* WordLower */] = 0,
    _a[1 /* WordUpper */] = 0,
    _a[2 /* WordNumber */] = 0,
    _a[3 /* End */] = 10,
    _a[4 /* Other */] = 2,
    _a[5 /* Separator */] = 30,
    _a[6 /* Space */] = 3,
    _a[7 /* LineBreakCR */] = 10,
    _a[8 /* LineBreakLF */] = 10,
    _a);
function getCategoryBoundaryScore(category) {
    return score[category];
}
function getCategory(charCode) {
    if (charCode === 10) {
        return 8 /* LineBreakLF */;
    }
    else if (charCode === 13) {
        return 7 /* LineBreakCR */;
    }
    else if (isSpace(charCode)) {
        return 6 /* Space */;
    }
    else if (charCode >= 97 && charCode <= 122) {
        return 0 /* WordLower */;
    }
    else if (charCode >= 65 && charCode <= 90) {
        return 1 /* WordUpper */;
    }
    else if (charCode >= 48 && charCode <= 57) {
        return 2 /* WordNumber */;
    }
    else if (charCode === -1) {
        return 3 /* End */;
    }
    else if (charCode === 44 || charCode === 59) {
        return 5 /* Separator */;
    }
    else {
        return 4 /* Other */;
    }
}
function computeMovedLines(changes, originalLines, modifiedLines, hashedOriginalLines, hashedModifiedLines, timeout) {
    var _a = computeMovesFromSimpleDeletionsToSimpleInsertions(changes, originalLines, modifiedLines, timeout), moves = _a.moves, excludedChanges = _a.excludedChanges;
    if (!timeout.isValid()) {
        return [];
    }
    var filteredChanges = changes.filter(function (c) { return !excludedChanges.has(c); });
    var unchangedMoves = computeUnchangedMoves(filteredChanges, hashedOriginalLines, hashedModifiedLines, originalLines, modifiedLines, timeout);
    pushMany(moves, unchangedMoves);
    moves = joinCloseConsecutiveMoves(moves);
    moves = moves.filter(function (current) {
        var lines = current.original.toOffsetRange().slice(originalLines).map(function (l) { return l.trim(); });
        var originalText = lines.join("\n");
        return originalText.length >= 15 && countWhere(lines, function (l) { return l.length >= 2; }) >= 2;
    });
    moves = removeMovesInSameDiff(changes, moves);
    return moves;
}
function countWhere(arr, predicate) {
    var e_6, _a;
    var count = 0;
    try {
        for (var arr_1 = __values(arr), arr_1_1 = arr_1.next(); !arr_1_1.done; arr_1_1 = arr_1.next()) {
            var t = arr_1_1.value;
            if (predicate(t)) {
                count++;
            }
        }
    }
    catch (e_6_1) { e_6 = { error: e_6_1 }; }
    finally {
        try {
            if (arr_1_1 && !arr_1_1.done && (_a = arr_1.return)) _a.call(arr_1);
        }
        finally { if (e_6) throw e_6.error; }
    }
    return count;
}
function computeMovesFromSimpleDeletionsToSimpleInsertions(changes, originalLines, modifiedLines, timeout) {
    var e_7, _a, e_8, _g;
    var moves = [];
    var deletions = changes.filter(function (c) { return c.modified.isEmpty && c.original.length >= 3; }).map(function (d) { return new LineRangeFragment(d.original, originalLines, d); });
    var insertions = new Set(changes.filter(function (c) { return c.original.isEmpty && c.modified.length >= 3; }).map(function (d) { return new LineRangeFragment(d.modified, modifiedLines, d); }));
    var excludedChanges = /* @__PURE__ */ new Set();
    try {
        for (var deletions_1 = __values(deletions), deletions_1_1 = deletions_1.next(); !deletions_1_1.done; deletions_1_1 = deletions_1.next()) {
            var deletion = deletions_1_1.value;
            var highestSimilarity = -1;
            var best = void 0;
            try {
                for (var insertions_1 = (e_8 = void 0, __values(insertions)), insertions_1_1 = insertions_1.next(); !insertions_1_1.done; insertions_1_1 = insertions_1.next()) {
                    var insertion = insertions_1_1.value;
                    var similarity = deletion.computeSimilarity(insertion);
                    if (similarity > highestSimilarity) {
                        highestSimilarity = similarity;
                        best = insertion;
                    }
                }
            }
            catch (e_8_1) { e_8 = { error: e_8_1 }; }
            finally {
                try {
                    if (insertions_1_1 && !insertions_1_1.done && (_g = insertions_1.return)) _g.call(insertions_1);
                }
                finally { if (e_8) throw e_8.error; }
            }
            if (highestSimilarity > 0.9 && best) {
                insertions.delete(best);
                moves.push(new LineRangeMapping(deletion.range, best.range));
                excludedChanges.add(deletion.source);
                excludedChanges.add(best.source);
            }
            if (!timeout.isValid()) {
                return { moves: moves, excludedChanges: excludedChanges };
            }
        }
    }
    catch (e_7_1) { e_7 = { error: e_7_1 }; }
    finally {
        try {
            if (deletions_1_1 && !deletions_1_1.done && (_a = deletions_1.return)) _a.call(deletions_1);
        }
        finally { if (e_7) throw e_7.error; }
    }
    return { moves: moves, excludedChanges: excludedChanges };
}
function computeUnchangedMoves(changes, hashedOriginalLines, hashedModifiedLines, originalLines, modifiedLines, timeout) {
    var e_9, _a, e_10, _g, e_11, _h, e_12, _j;
    var moves = [];
    var original3LineHashes = new SetMap();
    try {
        for (var changes_1 = __values(changes), changes_1_1 = changes_1.next(); !changes_1_1.done; changes_1_1 = changes_1.next()) {
            var change = changes_1_1.value;
            for (var i = change.original.startLineNumber; i < change.original.endLineNumberExclusive - 2; i++) {
                var key = "".concat(hashedOriginalLines[i - 1], ":").concat(hashedOriginalLines[i + 1 - 1], ":").concat(hashedOriginalLines[i + 2 - 1]);
                original3LineHashes.add(key, { range: new LineRange(i, i + 3) });
            }
        }
    }
    catch (e_9_1) { e_9 = { error: e_9_1 }; }
    finally {
        try {
            if (changes_1_1 && !changes_1_1.done && (_a = changes_1.return)) _a.call(changes_1);
        }
        finally { if (e_9) throw e_9.error; }
    }
    var possibleMappings = [];
    changes.sort(compareBy(function (c) { return c.modified.startLineNumber; }, numberComparator));
    var _loop_1 = function (change) {
        var lastMappings = [];
        var _loop_3 = function (i) {
            var key = "".concat(hashedModifiedLines[i - 1], ":").concat(hashedModifiedLines[i + 1 - 1], ":").concat(hashedModifiedLines[i + 2 - 1]);
            var currentModifiedRange = new LineRange(i, i + 3);
            var nextMappings = [];
            original3LineHashes.forEach(key, function (_a) {
                var e_13, _g;
                var range = _a.range;
                try {
                    for (var lastMappings_1 = (e_13 = void 0, __values(lastMappings)), lastMappings_1_1 = lastMappings_1.next(); !lastMappings_1_1.done; lastMappings_1_1 = lastMappings_1.next()) {
                        var lastMapping = lastMappings_1_1.value;
                        if (lastMapping.originalLineRange.endLineNumberExclusive + 1 === range.endLineNumberExclusive && lastMapping.modifiedLineRange.endLineNumberExclusive + 1 === currentModifiedRange.endLineNumberExclusive) {
                            lastMapping.originalLineRange = new LineRange(lastMapping.originalLineRange.startLineNumber, range.endLineNumberExclusive);
                            lastMapping.modifiedLineRange = new LineRange(lastMapping.modifiedLineRange.startLineNumber, currentModifiedRange.endLineNumberExclusive);
                            nextMappings.push(lastMapping);
                            return;
                        }
                    }
                }
                catch (e_13_1) { e_13 = { error: e_13_1 }; }
                finally {
                    try {
                        if (lastMappings_1_1 && !lastMappings_1_1.done && (_g = lastMappings_1.return)) _g.call(lastMappings_1);
                    }
                    finally { if (e_13) throw e_13.error; }
                }
                var mapping = {
                    modifiedLineRange: currentModifiedRange,
                    originalLineRange: range
                };
                possibleMappings.push(mapping);
                nextMappings.push(mapping);
            });
            lastMappings = nextMappings;
        };
        for (var i = change.modified.startLineNumber; i < change.modified.endLineNumberExclusive - 2; i++) {
            _loop_3(i);
        }
        if (!timeout.isValid()) {
            return { value: [] };
        }
    };
    try {
        for (var changes_2 = __values(changes), changes_2_1 = changes_2.next(); !changes_2_1.done; changes_2_1 = changes_2.next()) {
            var change = changes_2_1.value;
            var state_1 = _loop_1(change);
            if (typeof state_1 === "object")
                return state_1.value;
        }
    }
    catch (e_10_1) { e_10 = { error: e_10_1 }; }
    finally {
        try {
            if (changes_2_1 && !changes_2_1.done && (_g = changes_2.return)) _g.call(changes_2);
        }
        finally { if (e_10) throw e_10.error; }
    }
    possibleMappings.sort(reverseOrder(compareBy(function (m) { return m.modifiedLineRange.length; }, numberComparator)));
    var modifiedSet = new LineRangeSet();
    var originalSet = new LineRangeSet();
    try {
        for (var possibleMappings_1 = __values(possibleMappings), possibleMappings_1_1 = possibleMappings_1.next(); !possibleMappings_1_1.done; possibleMappings_1_1 = possibleMappings_1.next()) {
            var mapping = possibleMappings_1_1.value;
            var diffOrigToMod = mapping.modifiedLineRange.startLineNumber - mapping.originalLineRange.startLineNumber;
            var modifiedSections = modifiedSet.subtractFrom(mapping.modifiedLineRange);
            var originalTranslatedSections = originalSet.subtractFrom(mapping.originalLineRange).getWithDelta(diffOrigToMod);
            var modifiedIntersectedSections = modifiedSections.getIntersection(originalTranslatedSections);
            try {
                for (var _k = (e_12 = void 0, __values(modifiedIntersectedSections.ranges)), _l = _k.next(); !_l.done; _l = _k.next()) {
                    var s = _l.value;
                    if (s.length < 3) {
                        continue;
                    }
                    var modifiedLineRange = s;
                    var originalLineRange = s.delta(-diffOrigToMod);
                    moves.push(new LineRangeMapping(originalLineRange, modifiedLineRange));
                    modifiedSet.addRange(modifiedLineRange);
                    originalSet.addRange(originalLineRange);
                }
            }
            catch (e_12_1) { e_12 = { error: e_12_1 }; }
            finally {
                try {
                    if (_l && !_l.done && (_j = _k.return)) _j.call(_k);
                }
                finally { if (e_12) throw e_12.error; }
            }
        }
    }
    catch (e_11_1) { e_11 = { error: e_11_1 }; }
    finally {
        try {
            if (possibleMappings_1_1 && !possibleMappings_1_1.done && (_h = possibleMappings_1.return)) _h.call(possibleMappings_1);
        }
        finally { if (e_11) throw e_11.error; }
    }
    moves.sort(compareBy(function (m) { return m.original.startLineNumber; }, numberComparator));
    var monotonousChanges = new MonotonousArray(changes);
    var _loop_2 = function (i) {
        var move = moves[i];
        var firstTouchingChangeOrig = monotonousChanges.findLastMonotonous(function (c) { return c.original.startLineNumber <= move.original.startLineNumber; });
        var firstTouchingChangeMod = findLastMonotonous(changes, function (c) { return c.modified.startLineNumber <= move.modified.startLineNumber; });
        var linesAbove = Math.max(move.original.startLineNumber - firstTouchingChangeOrig.original.startLineNumber, move.modified.startLineNumber - firstTouchingChangeMod.modified.startLineNumber);
        var lastTouchingChangeOrig = monotonousChanges.findLastMonotonous(function (c) { return c.original.startLineNumber < move.original.endLineNumberExclusive; });
        var lastTouchingChangeMod = findLastMonotonous(changes, function (c) { return c.modified.startLineNumber < move.modified.endLineNumberExclusive; });
        var linesBelow = Math.max(lastTouchingChangeOrig.original.endLineNumberExclusive - move.original.endLineNumberExclusive, lastTouchingChangeMod.modified.endLineNumberExclusive - move.modified.endLineNumberExclusive);
        var extendToTop = void 0;
        for (extendToTop = 0; extendToTop < linesAbove; extendToTop++) {
            var origLine = move.original.startLineNumber - extendToTop - 1;
            var modLine = move.modified.startLineNumber - extendToTop - 1;
            if (origLine > originalLines.length || modLine > modifiedLines.length) {
                break;
            }
            if (modifiedSet.contains(modLine) || originalSet.contains(origLine)) {
                break;
            }
            if (!areLinesSimilar(originalLines[origLine - 1], modifiedLines[modLine - 1], timeout)) {
                break;
            }
        }
        if (extendToTop > 0) {
            originalSet.addRange(new LineRange(move.original.startLineNumber - extendToTop, move.original.startLineNumber));
            modifiedSet.addRange(new LineRange(move.modified.startLineNumber - extendToTop, move.modified.startLineNumber));
        }
        var extendToBottom = void 0;
        for (extendToBottom = 0; extendToBottom < linesBelow; extendToBottom++) {
            var origLine = move.original.endLineNumberExclusive + extendToBottom;
            var modLine = move.modified.endLineNumberExclusive + extendToBottom;
            if (origLine > originalLines.length || modLine > modifiedLines.length) {
                break;
            }
            if (modifiedSet.contains(modLine) || originalSet.contains(origLine)) {
                break;
            }
            if (!areLinesSimilar(originalLines[origLine - 1], modifiedLines[modLine - 1], timeout)) {
                break;
            }
        }
        if (extendToBottom > 0) {
            originalSet.addRange(new LineRange(move.original.endLineNumberExclusive, move.original.endLineNumberExclusive + extendToBottom));
            modifiedSet.addRange(new LineRange(move.modified.endLineNumberExclusive, move.modified.endLineNumberExclusive + extendToBottom));
        }
        if (extendToTop > 0 || extendToBottom > 0) {
            moves[i] = new LineRangeMapping(new LineRange(move.original.startLineNumber - extendToTop, move.original.endLineNumberExclusive + extendToBottom), new LineRange(move.modified.startLineNumber - extendToTop, move.modified.endLineNumberExclusive + extendToBottom));
        }
    };
    for (var i = 0; i < moves.length; i++) {
        _loop_2(i);
    }
    return moves;
}
function areLinesSimilar(line1, line2, timeout) {
    var e_14, _a;
    if (line1.trim() === line2.trim()) {
        return true;
    }
    if (line1.length > 300 && line2.length > 300) {
        return false;
    }
    var myersDiffingAlgorithm = new MyersDiffAlgorithm();
    var result = myersDiffingAlgorithm.compute(new LinesSliceCharSequence([line1], new Range(1, 1, 1, line1.length), false), new LinesSliceCharSequence([line2], new Range(1, 1, 1, line2.length), false), timeout);
    var commonNonSpaceCharCount = 0;
    var inverted = SequenceDiff.invert(result.diffs, line1.length);
    try {
        for (var inverted_1 = __values(inverted), inverted_1_1 = inverted_1.next(); !inverted_1_1.done; inverted_1_1 = inverted_1.next()) {
            var seq = inverted_1_1.value;
            seq.seq1Range.forEach(function (idx) {
                if (!isSpace(line1.charCodeAt(idx))) {
                    commonNonSpaceCharCount++;
                }
            });
        }
    }
    catch (e_14_1) { e_14 = { error: e_14_1 }; }
    finally {
        try {
            if (inverted_1_1 && !inverted_1_1.done && (_a = inverted_1.return)) _a.call(inverted_1);
        }
        finally { if (e_14) throw e_14.error; }
    }
    function countNonWsChars(str) {
        var count = 0;
        for (var i = 0; i < line1.length; i++) {
            if (!isSpace(str.charCodeAt(i))) {
                count++;
            }
        }
        return count;
    }
    var longerLineLength = countNonWsChars(line1.length > line2.length ? line1 : line2);
    var r = commonNonSpaceCharCount / longerLineLength > 0.6 && longerLineLength > 10;
    return r;
}
function joinCloseConsecutiveMoves(moves) {
    if (moves.length === 0) {
        return moves;
    }
    moves.sort(compareBy(function (m) { return m.original.startLineNumber; }, numberComparator));
    var result = [moves[0]];
    for (var i = 1; i < moves.length; i++) {
        var last = result[result.length - 1];
        var current = moves[i];
        var originalDist = current.original.startLineNumber - last.original.endLineNumberExclusive;
        var modifiedDist = current.modified.startLineNumber - last.modified.endLineNumberExclusive;
        var currentMoveAfterLast = originalDist >= 0 && modifiedDist >= 0;
        if (currentMoveAfterLast && originalDist + modifiedDist <= 2) {
            result[result.length - 1] = last.join(current);
            continue;
        }
        result.push(current);
    }
    return result;
}
function removeMovesInSameDiff(changes, moves) {
    var changesMonotonous = new MonotonousArray(changes);
    moves = moves.filter(function (m) {
        var diffBeforeEndOfMoveOriginal = changesMonotonous.findLastMonotonous(function (c) { return c.original.startLineNumber < m.original.endLineNumberExclusive; }) || new LineRangeMapping(new LineRange(1, 1), new LineRange(1, 1));
        var diffBeforeEndOfMoveModified = findLastMonotonous(changes, function (c) { return c.modified.startLineNumber < m.modified.endLineNumberExclusive; });
        var differentDiffs = diffBeforeEndOfMoveOriginal !== diffBeforeEndOfMoveModified;
        return differentDiffs;
    });
    return moves;
}
function optimizeSequenceDiffs(sequence1, sequence2, sequenceDiffs) {
    var result = sequenceDiffs;
    result = joinSequenceDiffsByShifting(sequence1, sequence2, result);
    result = joinSequenceDiffsByShifting(sequence1, sequence2, result);
    result = shiftSequenceDiffs(sequence1, sequence2, result);
    return result;
}
function joinSequenceDiffsByShifting(sequence1, sequence2, sequenceDiffs) {
    if (sequenceDiffs.length === 0) {
        return sequenceDiffs;
    }
    var result = [];
    result.push(sequenceDiffs[0]);
    for (var i = 1; i < sequenceDiffs.length; i++) {
        var prevResult = result[result.length - 1];
        var cur = sequenceDiffs[i];
        if (cur.seq1Range.isEmpty || cur.seq2Range.isEmpty) {
            var length = cur.seq1Range.start - prevResult.seq1Range.endExclusive;
            var d = void 0;
            for (d = 1; d <= length; d++) {
                if (sequence1.getElement(cur.seq1Range.start - d) !== sequence1.getElement(cur.seq1Range.endExclusive - d) || sequence2.getElement(cur.seq2Range.start - d) !== sequence2.getElement(cur.seq2Range.endExclusive - d)) {
                    break;
                }
            }
            d--;
            if (d === length) {
                result[result.length - 1] = new SequenceDiff(new OffsetRange(prevResult.seq1Range.start, cur.seq1Range.endExclusive - length), new OffsetRange(prevResult.seq2Range.start, cur.seq2Range.endExclusive - length));
                continue;
            }
            cur = cur.delta(-d);
        }
        result.push(cur);
    }
    var result2 = [];
    for (var i = 0; i < result.length - 1; i++) {
        var nextResult = result[i + 1];
        var cur = result[i];
        if (cur.seq1Range.isEmpty || cur.seq2Range.isEmpty) {
            var length = nextResult.seq1Range.start - cur.seq1Range.endExclusive;
            var d = void 0;
            for (d = 0; d < length; d++) {
                if (!sequence1.isStronglyEqual(cur.seq1Range.start + d, cur.seq1Range.endExclusive + d) || !sequence2.isStronglyEqual(cur.seq2Range.start + d, cur.seq2Range.endExclusive + d)) {
                    break;
                }
            }
            if (d === length) {
                result[i + 1] = new SequenceDiff(new OffsetRange(cur.seq1Range.start + length, nextResult.seq1Range.endExclusive), new OffsetRange(cur.seq2Range.start + length, nextResult.seq2Range.endExclusive));
                continue;
            }
            if (d > 0) {
                cur = cur.delta(d);
            }
        }
        result2.push(cur);
    }
    if (result.length > 0) {
        result2.push(result[result.length - 1]);
    }
    return result2;
}
function shiftSequenceDiffs(sequence1, sequence2, sequenceDiffs) {
    if (!sequence1.getBoundaryScore || !sequence2.getBoundaryScore) {
        return sequenceDiffs;
    }
    for (var i = 0; i < sequenceDiffs.length; i++) {
        var prevDiff = i > 0 ? sequenceDiffs[i - 1] : undefined;
        var diff = sequenceDiffs[i];
        var nextDiff = i + 1 < sequenceDiffs.length ? sequenceDiffs[i + 1] : undefined;
        var seq1ValidRange = new OffsetRange(prevDiff ? prevDiff.seq1Range.endExclusive + 1 : 0, nextDiff ? nextDiff.seq1Range.start - 1 : sequence1.length);
        var seq2ValidRange = new OffsetRange(prevDiff ? prevDiff.seq2Range.endExclusive + 1 : 0, nextDiff ? nextDiff.seq2Range.start - 1 : sequence2.length);
        if (diff.seq1Range.isEmpty) {
            sequenceDiffs[i] = shiftDiffToBetterPosition(diff, sequence1, sequence2, seq1ValidRange, seq2ValidRange);
        }
        else if (diff.seq2Range.isEmpty) {
            sequenceDiffs[i] = shiftDiffToBetterPosition(diff.swap(), sequence2, sequence1, seq2ValidRange, seq1ValidRange).swap();
        }
    }
    return sequenceDiffs;
}
function shiftDiffToBetterPosition(diff, sequence1, sequence2, seq1ValidRange, seq2ValidRange) {
    var maxShiftLimit = 100;
    var deltaBefore = 1;
    while (diff.seq1Range.start - deltaBefore >= seq1ValidRange.start && diff.seq2Range.start - deltaBefore >= seq2ValidRange.start && sequence2.isStronglyEqual(diff.seq2Range.start - deltaBefore, diff.seq2Range.endExclusive - deltaBefore) && deltaBefore < maxShiftLimit) {
        deltaBefore++;
    }
    deltaBefore--;
    var deltaAfter = 0;
    while (diff.seq1Range.start + deltaAfter < seq1ValidRange.endExclusive && diff.seq2Range.endExclusive + deltaAfter < seq2ValidRange.endExclusive && sequence2.isStronglyEqual(diff.seq2Range.start + deltaAfter, diff.seq2Range.endExclusive + deltaAfter) && deltaAfter < maxShiftLimit) {
        deltaAfter++;
    }
    if (deltaBefore === 0 && deltaAfter === 0) {
        return diff;
    }
    var bestDelta = 0;
    var bestScore = -1;
    for (var delta = -deltaBefore; delta <= deltaAfter; delta++) {
        var seq2OffsetStart = diff.seq2Range.start + delta;
        var seq2OffsetEndExclusive = diff.seq2Range.endExclusive + delta;
        var seq1Offset = diff.seq1Range.start + delta;
        var score_1 = sequence1.getBoundaryScore(seq1Offset) + sequence2.getBoundaryScore(seq2OffsetStart) + sequence2.getBoundaryScore(seq2OffsetEndExclusive);
        if (score_1 > bestScore) {
            bestScore = score_1;
            bestDelta = delta;
        }
    }
    return diff.delta(bestDelta);
}
function removeShortMatches(sequence1, sequence2, sequenceDiffs) {
    var e_15, _a;
    var result = [];
    try {
        for (var sequenceDiffs_2 = __values(sequenceDiffs), sequenceDiffs_2_1 = sequenceDiffs_2.next(); !sequenceDiffs_2_1.done; sequenceDiffs_2_1 = sequenceDiffs_2.next()) {
            var s = sequenceDiffs_2_1.value;
            var last = result[result.length - 1];
            if (!last) {
                result.push(s);
                continue;
            }
            if (s.seq1Range.start - last.seq1Range.endExclusive <= 2 || s.seq2Range.start - last.seq2Range.endExclusive <= 2) {
                result[result.length - 1] = new SequenceDiff(last.seq1Range.join(s.seq1Range), last.seq2Range.join(s.seq2Range));
            }
            else {
                result.push(s);
            }
        }
    }
    catch (e_15_1) { e_15 = { error: e_15_1 }; }
    finally {
        try {
            if (sequenceDiffs_2_1 && !sequenceDiffs_2_1.done && (_a = sequenceDiffs_2.return)) _a.call(sequenceDiffs_2);
        }
        finally { if (e_15) throw e_15.error; }
    }
    return result;
}
function extendDiffsToEntireWordIfAppropriate(sequence1, sequence2, sequenceDiffs, findParent, force) {
    if (force === void 0) { force = false; }
    var equalMappings = SequenceDiff.invert(sequenceDiffs, sequence1.length);
    var additional = [];
    var lastPoint = new OffsetPair(0, 0);
    function scanWord(pair, equalMapping) {
        if (pair.offset1 < lastPoint.offset1 || pair.offset2 < lastPoint.offset2) {
            return;
        }
        var w1 = findParent(sequence1, pair.offset1);
        var w2 = findParent(sequence2, pair.offset2);
        if (!w1 || !w2) {
            return;
        }
        var w = new SequenceDiff(w1, w2);
        var equalPart = w.intersect(equalMapping);
        var equalChars1 = equalPart.seq1Range.length;
        var equalChars2 = equalPart.seq2Range.length;
        while (equalMappings.length > 0) {
            var next = equalMappings[0];
            var intersects = next.seq1Range.intersects(w.seq1Range) || next.seq2Range.intersects(w.seq2Range);
            if (!intersects) {
                break;
            }
            var v1 = findParent(sequence1, next.seq1Range.start);
            var v2 = findParent(sequence2, next.seq2Range.start);
            var v = new SequenceDiff(v1, v2);
            var equalPart2 = v.intersect(next);
            equalChars1 += equalPart2.seq1Range.length;
            equalChars2 += equalPart2.seq2Range.length;
            w = w.join(v);
            if (w.seq1Range.endExclusive >= next.seq1Range.endExclusive) {
                equalMappings.shift();
            }
            else {
                break;
            }
        }
        if (force && equalChars1 + equalChars2 < w.seq1Range.length + w.seq2Range.length || equalChars1 + equalChars2 < (w.seq1Range.length + w.seq2Range.length) * 2 / 3) {
            additional.push(w);
        }
        lastPoint = w.getEndExclusives();
    }
    while (equalMappings.length > 0) {
        var next = equalMappings.shift();
        if (next.seq1Range.isEmpty) {
            continue;
        }
        scanWord(next.getStarts(), next);
        scanWord(next.getEndExclusives().delta(-1), next);
    }
    var merged = mergeSequenceDiffs(sequenceDiffs, additional);
    return merged;
}
function mergeSequenceDiffs(sequenceDiffs1, sequenceDiffs2) {
    var result = [];
    while (sequenceDiffs1.length > 0 || sequenceDiffs2.length > 0) {
        var sd1 = sequenceDiffs1[0];
        var sd2 = sequenceDiffs2[0];
        var next = void 0;
        if (sd1 && (!sd2 || sd1.seq1Range.start < sd2.seq1Range.start)) {
            next = sequenceDiffs1.shift();
        }
        else {
            next = sequenceDiffs2.shift();
        }
        if (result.length > 0 && result[result.length - 1].seq1Range.endExclusive >= next.seq1Range.start) {
            result[result.length - 1] = result[result.length - 1].join(next);
        }
        else {
            result.push(next);
        }
    }
    return result;
}
function removeVeryShortMatchingLinesBetweenDiffs(sequence1, _sequence2, sequenceDiffs) {
    var diffs = sequenceDiffs;
    if (diffs.length === 0) {
        return diffs;
    }
    var counter = 0;
    var shouldRepeat;
    do {
        shouldRepeat = false;
        var result = [
            diffs[0]
        ];
        var _loop_4 = function (i) {
            var shouldJoinDiffs = function (before, after) {
                var unchangedRange = new OffsetRange(lastResult.seq1Range.endExclusive, cur.seq1Range.start);
                var unchangedText = sequence1.getText(unchangedRange);
                var unchangedTextWithoutWs = unchangedText.replace(/\s/g, "");
                if (unchangedTextWithoutWs.length <= 4 && (before.seq1Range.length + before.seq2Range.length > 5 || after.seq1Range.length + after.seq2Range.length > 5)) {
                    return true;
                }
                return false;
            };
            var cur = diffs[i];
            var lastResult = result[result.length - 1];
            var shouldJoin = shouldJoinDiffs(lastResult, cur);
            if (shouldJoin) {
                shouldRepeat = true;
                result[result.length - 1] = result[result.length - 1].join(cur);
            }
            else {
                result.push(cur);
            }
        };
        for (var i = 1; i < diffs.length; i++) {
            _loop_4(i);
        }
        diffs = result;
    } while (counter++ < 10 && shouldRepeat);
    return diffs;
}
function removeVeryShortMatchingTextBetweenLongDiffs(sequence1, sequence2, sequenceDiffs) {
    var diffs = sequenceDiffs;
    if (diffs.length === 0) {
        return diffs;
    }
    var counter = 0;
    var shouldRepeat;
    do {
        shouldRepeat = false;
        var result = [
            diffs[0]
        ];
        var _loop_5 = function (i) {
            var shouldJoinDiffs = function (before, after) {
                var unchangedRange = new OffsetRange(lastResult.seq1Range.endExclusive, cur.seq1Range.start);
                var unchangedLineCount = sequence1.countLinesIn(unchangedRange);
                if (unchangedLineCount > 5 || unchangedRange.length > 500) {
                    return false;
                }
                var unchangedText = sequence1.getText(unchangedRange).trim();
                if (unchangedText.length > 20 || unchangedText.split(/\r\n|\r|\n/).length > 1) {
                    return false;
                }
                var beforeLineCount1 = sequence1.countLinesIn(before.seq1Range);
                var beforeSeq1Length = before.seq1Range.length;
                var beforeLineCount2 = sequence2.countLinesIn(before.seq2Range);
                var beforeSeq2Length = before.seq2Range.length;
                var afterLineCount1 = sequence1.countLinesIn(after.seq1Range);
                var afterSeq1Length = after.seq1Range.length;
                var afterLineCount2 = sequence2.countLinesIn(after.seq2Range);
                var afterSeq2Length = after.seq2Range.length;
                var max = 2 * 40 + 50;
                function cap(v) {
                    return Math.min(v, max);
                }
                if (Math.pow(Math.pow(cap(beforeLineCount1 * 40 + beforeSeq1Length), 1.5) + Math.pow(cap(beforeLineCount2 * 40 + beforeSeq2Length), 1.5), 1.5) + Math.pow(Math.pow(cap(afterLineCount1 * 40 + afterSeq1Length), 1.5) + Math.pow(cap(afterLineCount2 * 40 + afterSeq2Length), 1.5), 1.5) > Math.pow((Math.pow(max, 1.5)), 1.5) * 1.3) {
                    return true;
                }
                return false;
            };
            var cur = diffs[i];
            var lastResult = result[result.length - 1];
            var shouldJoin = shouldJoinDiffs(lastResult, cur);
            if (shouldJoin) {
                shouldRepeat = true;
                result[result.length - 1] = result[result.length - 1].join(cur);
            }
            else {
                result.push(cur);
            }
        };
        for (var i = 1; i < diffs.length; i++) {
            _loop_5(i);
        }
        diffs = result;
    } while (counter++ < 10 && shouldRepeat);
    var newDiffs = [];
    forEachWithNeighbors(diffs, function (prev, cur, next) {
        var newDiff = cur;
        function shouldMarkAsChanged(text) {
            return text.length > 0 && text.trim().length <= 3 && cur.seq1Range.length + cur.seq2Range.length > 100;
        }
        var fullRange1 = sequence1.extendToFullLines(cur.seq1Range);
        var prefix = sequence1.getText(new OffsetRange(fullRange1.start, cur.seq1Range.start));
        if (shouldMarkAsChanged(prefix)) {
            newDiff = newDiff.deltaStart(-prefix.length);
        }
        var suffix = sequence1.getText(new OffsetRange(cur.seq1Range.endExclusive, fullRange1.endExclusive));
        if (shouldMarkAsChanged(suffix)) {
            newDiff = newDiff.deltaEnd(suffix.length);
        }
        var availableSpace = SequenceDiff.fromOffsetPairs(prev ? prev.getEndExclusives() : OffsetPair.zero, next ? next.getStarts() : OffsetPair.max);
        var result = newDiff.intersect(availableSpace);
        if (newDiffs.length > 0 && result.getStarts().equals(newDiffs[newDiffs.length - 1].getEndExclusives())) {
            newDiffs[newDiffs.length - 1] = newDiffs[newDiffs.length - 1].join(result);
        }
        else {
            newDiffs.push(result);
        }
    });
    return newDiffs;
}
var LineSequence = /** @class */ (function () {
    function LineSequence(trimmedHash, lines) {
        this.trimmedHash = trimmedHash;
        this.lines = lines;
    }
    LineSequence.prototype.getElement = function (offset) {
        return this.trimmedHash[offset];
    };
    Object.defineProperty(LineSequence.prototype, "length", {
        get: function () {
            return this.trimmedHash.length;
        },
        enumerable: false,
        configurable: true
    });
    LineSequence.prototype.getBoundaryScore = function (length) {
        var indentationBefore = length === 0 ? 0 : getIndentation(this.lines[length - 1]);
        var indentationAfter = length === this.lines.length ? 0 : getIndentation(this.lines[length]);
        return 1e3 - (indentationBefore + indentationAfter);
    };
    LineSequence.prototype.getText = function (range) {
        return this.lines.slice(range.start, range.endExclusive).join("\n");
    };
    LineSequence.prototype.isStronglyEqual = function (offset1, offset2) {
        return this.lines[offset1] === this.lines[offset2];
    };
    return LineSequence;
}());
function getIndentation(str) {
    var i = 0;
    while (i < str.length && (str.charCodeAt(i) === 32 || str.charCodeAt(i) === 9)) {
        i++;
    }
    return i;
}
var DefaultLinesDiffComputer = /** @class */ (function () {
    function DefaultLinesDiffComputer() {
        this.dynamicProgrammingDiffing = new DynamicProgrammingDiffing();
        this.myersDiffingAlgorithm = new MyersDiffAlgorithm();
    }
    DefaultLinesDiffComputer.prototype.computeDiff = function (originalLines, modifiedLines, options) {
        var e_16, _a;
        var _this = this;
        if (originalLines.length <= 1 && equals(originalLines, modifiedLines, function (a, b) { return a === b; })) {
            return new LinesDiff([], [], false);
        }
        if (originalLines.length === 1 && originalLines[0].length === 0 || modifiedLines.length === 1 && modifiedLines[0].length === 0) {
            return new LinesDiff([
                new DetailedLineRangeMapping(new LineRange(1, originalLines.length + 1), new LineRange(1, modifiedLines.length + 1), [
                    new RangeMapping(new Range(1, 1, originalLines.length, originalLines[originalLines.length - 1].length + 1), new Range(1, 1, modifiedLines.length, modifiedLines[modifiedLines.length - 1].length + 1))
                ])
            ], [], false);
        }
        var timeout = options.maxComputationTimeMs === 0 ? InfiniteTimeout.instance : new DateTimeout(options.maxComputationTimeMs);
        var considerWhitespaceChanges = !options.ignoreTrimWhitespace;
        var perfectHashes = /* @__PURE__ */ new Map();
        function getOrCreateHash(text) {
            var hash = perfectHashes.get(text);
            if (hash === undefined) {
                hash = perfectHashes.size;
                perfectHashes.set(text, hash);
            }
            return hash;
        }
        var originalLinesHashes = originalLines.map(function (l) { return getOrCreateHash(l.trim()); });
        var modifiedLinesHashes = modifiedLines.map(function (l) { return getOrCreateHash(l.trim()); });
        var sequence1 = new LineSequence(originalLinesHashes, originalLines);
        var sequence2 = new LineSequence(modifiedLinesHashes, modifiedLines);
        var lineAlignmentResult = (function () {
            if (sequence1.length + sequence2.length < 1700) {
                return _this.dynamicProgrammingDiffing.compute(sequence1, sequence2, timeout, function (offset1, offset2) { return originalLines[offset1] === modifiedLines[offset2] ? modifiedLines[offset2].length === 0 ? 0.1 : 1 + Math.log(1 + modifiedLines[offset2].length) : 0.99; });
            }
            return _this.myersDiffingAlgorithm.compute(sequence1, sequence2, timeout);
        })();
        var lineAlignments = lineAlignmentResult.diffs;
        var hitTimeout = lineAlignmentResult.hitTimeout;
        lineAlignments = optimizeSequenceDiffs(sequence1, sequence2, lineAlignments);
        lineAlignments = removeVeryShortMatchingLinesBetweenDiffs(sequence1, sequence2, lineAlignments);
        var alignments = [];
        var scanForWhitespaceChanges = function (equalLinesCount) {
            var e_17, _a;
            if (!considerWhitespaceChanges) {
                return;
            }
            for (var i = 0; i < equalLinesCount; i++) {
                var seq1Offset = seq1LastStart + i;
                var seq2Offset = seq2LastStart + i;
                if (originalLines[seq1Offset] !== modifiedLines[seq2Offset]) {
                    var characterDiffs = _this.refineDiff(originalLines, modifiedLines, new SequenceDiff(new OffsetRange(seq1Offset, seq1Offset + 1), new OffsetRange(seq2Offset, seq2Offset + 1)), timeout, considerWhitespaceChanges, options);
                    try {
                        for (var _g = (e_17 = void 0, __values(characterDiffs.mappings)), _h = _g.next(); !_h.done; _h = _g.next()) {
                            var a = _h.value;
                            alignments.push(a);
                        }
                    }
                    catch (e_17_1) { e_17 = { error: e_17_1 }; }
                    finally {
                        try {
                            if (_h && !_h.done && (_a = _g.return)) _a.call(_g);
                        }
                        finally { if (e_17) throw e_17.error; }
                    }
                    if (characterDiffs.hitTimeout) {
                        hitTimeout = true;
                    }
                }
            }
        };
        var seq1LastStart = 0;
        var seq2LastStart = 0;
        var _loop_6 = function (diff) {
            var e_18, _g;
            assertFn(function () { return diff.seq1Range.start - seq1LastStart === diff.seq2Range.start - seq2LastStart; });
            var equalLinesCount = diff.seq1Range.start - seq1LastStart;
            scanForWhitespaceChanges(equalLinesCount);
            seq1LastStart = diff.seq1Range.endExclusive;
            seq2LastStart = diff.seq2Range.endExclusive;
            var characterDiffs = this_1.refineDiff(originalLines, modifiedLines, diff, timeout, considerWhitespaceChanges, options);
            if (characterDiffs.hitTimeout) {
                hitTimeout = true;
            }
            try {
                for (var _h = (e_18 = void 0, __values(characterDiffs.mappings)), _j = _h.next(); !_j.done; _j = _h.next()) {
                    var a = _j.value;
                    alignments.push(a);
                }
            }
            catch (e_18_1) { e_18 = { error: e_18_1 }; }
            finally {
                try {
                    if (_j && !_j.done && (_g = _h.return)) _g.call(_h);
                }
                finally { if (e_18) throw e_18.error; }
            }
        };
        var this_1 = this;
        try {
            for (var lineAlignments_1 = __values(lineAlignments), lineAlignments_1_1 = lineAlignments_1.next(); !lineAlignments_1_1.done; lineAlignments_1_1 = lineAlignments_1.next()) {
                var diff = lineAlignments_1_1.value;
                _loop_6(diff);
            }
        }
        catch (e_16_1) { e_16 = { error: e_16_1 }; }
        finally {
            try {
                if (lineAlignments_1_1 && !lineAlignments_1_1.done && (_a = lineAlignments_1.return)) _a.call(lineAlignments_1);
            }
            finally { if (e_16) throw e_16.error; }
        }
        scanForWhitespaceChanges(originalLines.length - seq1LastStart);
        var changes = lineRangeMappingFromRangeMappings(alignments, new ArrayText(originalLines), new ArrayText(modifiedLines));
        var moves = [];
        if (options.computeMoves) {
            moves = this.computeMoves(changes, originalLines, modifiedLines, originalLinesHashes, modifiedLinesHashes, timeout, considerWhitespaceChanges, options);
        }
        assertFn(function () {
            var e_19, _a, e_20, _g;
            function validatePosition(pos, lines) {
                if (pos.lineNumber < 1 || pos.lineNumber > lines.length) {
                    return false;
                }
                var line = lines[pos.lineNumber - 1];
                if (pos.column < 1 || pos.column > line.length + 1) {
                    return false;
                }
                return true;
            }
            function validateRange(range, lines) {
                if (range.startLineNumber < 1 || range.startLineNumber > lines.length + 1) {
                    return false;
                }
                if (range.endLineNumberExclusive < 1 || range.endLineNumberExclusive > lines.length + 1) {
                    return false;
                }
                return true;
            }
            try {
                for (var changes_3 = __values(changes), changes_3_1 = changes_3.next(); !changes_3_1.done; changes_3_1 = changes_3.next()) {
                    var c = changes_3_1.value;
                    if (!c.innerChanges) {
                        return false;
                    }
                    try {
                        for (var _h = (e_20 = void 0, __values(c.innerChanges)), _j = _h.next(); !_j.done; _j = _h.next()) {
                            var ic = _j.value;
                            var valid = validatePosition(ic.modifiedRange.getStartPosition(), modifiedLines) && validatePosition(ic.modifiedRange.getEndPosition(), modifiedLines) && validatePosition(ic.originalRange.getStartPosition(), originalLines) && validatePosition(ic.originalRange.getEndPosition(), originalLines);
                            if (!valid) {
                                return false;
                            }
                        }
                    }
                    catch (e_20_1) { e_20 = { error: e_20_1 }; }
                    finally {
                        try {
                            if (_j && !_j.done && (_g = _h.return)) _g.call(_h);
                        }
                        finally { if (e_20) throw e_20.error; }
                    }
                    if (!validateRange(c.modified, modifiedLines) || !validateRange(c.original, originalLines)) {
                        return false;
                    }
                }
            }
            catch (e_19_1) { e_19 = { error: e_19_1 }; }
            finally {
                try {
                    if (changes_3_1 && !changes_3_1.done && (_a = changes_3.return)) _a.call(changes_3);
                }
                finally { if (e_19) throw e_19.error; }
            }
            return true;
        });
        return new LinesDiff(changes, moves, hitTimeout);
    };
    DefaultLinesDiffComputer.prototype.computeMoves = function (changes, originalLines, modifiedLines, hashedOriginalLines, hashedModifiedLines, timeout, considerWhitespaceChanges, options) {
        var _this = this;
        var moves = computeMovedLines(changes, originalLines, modifiedLines, hashedOriginalLines, hashedModifiedLines, timeout);
        var movesWithDiffs = moves.map(function (m) {
            var moveChanges = _this.refineDiff(originalLines, modifiedLines, new SequenceDiff(m.original.toOffsetRange(), m.modified.toOffsetRange()), timeout, considerWhitespaceChanges, options);
            var mappings = lineRangeMappingFromRangeMappings(moveChanges.mappings, new ArrayText(originalLines), new ArrayText(modifiedLines), true);
            return new MovedText(m, mappings);
        });
        return movesWithDiffs;
    };
    DefaultLinesDiffComputer.prototype.refineDiff = function (originalLines, modifiedLines, diff, timeout, considerWhitespaceChanges, options) {
        var lineRangeMapping = toLineRangeMapping(diff);
        var rangeMapping = lineRangeMapping.toRangeMapping2(originalLines, modifiedLines);
        var slice1 = new LinesSliceCharSequence(originalLines, rangeMapping.originalRange, considerWhitespaceChanges);
        var slice2 = new LinesSliceCharSequence(modifiedLines, rangeMapping.modifiedRange, considerWhitespaceChanges);
        var diffResult = slice1.length + slice2.length < 500 ? this.dynamicProgrammingDiffing.compute(slice1, slice2, timeout) : this.myersDiffingAlgorithm.compute(slice1, slice2, timeout);
        var diffs = diffResult.diffs;
        diffs = optimizeSequenceDiffs(slice1, slice2, diffs);
        diffs = extendDiffsToEntireWordIfAppropriate(slice1, slice2, diffs, function (seq, idx) { return seq.findWordContaining(idx); });
        if (options.extendToSubwords) {
            diffs = extendDiffsToEntireWordIfAppropriate(slice1, slice2, diffs, function (seq, idx) { return seq.findSubWordContaining(idx); }, true);
        }
        diffs = removeShortMatches(slice1, slice2, diffs);
        diffs = removeVeryShortMatchingTextBetweenLongDiffs(slice1, slice2, diffs);
        var result = diffs.map(function (d) { return new RangeMapping(slice1.translateRange(d.seq1Range), slice2.translateRange(d.seq2Range)); });
        return {
            mappings: result,
            hitTimeout: diffResult.hitTimeout
        };
    };
    return DefaultLinesDiffComputer;
}());
function toLineRangeMapping(sequenceDiff) {
    return new LineRangeMapping(new LineRange(sequenceDiff.seq1Range.start + 1, sequenceDiff.seq1Range.endExclusive + 1), new LineRange(sequenceDiff.seq2Range.start + 1, sequenceDiff.seq2Range.endExclusive + 1));
}
function computeDiff(originalLines, modifiedLines, options) {
    var diffComputer = new DefaultLinesDiffComputer();
    var result = diffComputer.computeDiff(originalLines, modifiedLines, options);
    return result === null || result === void 0 ? void 0 : result.changes.map(function (changes) {
        var originalStartLineNumber;
        var originalEndLineNumber;
        var modifiedStartLineNumber;
        var modifiedEndLineNumber;
        var innerChanges = changes.innerChanges;
        originalStartLineNumber = changes.original.startLineNumber - 1;
        originalEndLineNumber = changes.original.endLineNumberExclusive - 1;
        modifiedStartLineNumber = changes.modified.startLineNumber - 1;
        modifiedEndLineNumber = changes.modified.endLineNumberExclusive - 1;
        return {
            origStart: originalStartLineNumber,
            origEnd: originalEndLineNumber,
            editStart: modifiedStartLineNumber,
            editEnd: modifiedEndLineNumber,
            charChanges: innerChanges === null || innerChanges === void 0 ? void 0 : innerChanges.map(function (m) { return ({
                originalStartLineNumber: m.originalRange.startLineNumber - 1,
                originalStartColumn: m.originalRange.startColumn - 1,
                originalEndLineNumber: m.originalRange.endLineNumber - 1,
                originalEndColumn: m.originalRange.endColumn - 1,
                modifiedStartLineNumber: m.modifiedRange.startLineNumber - 1,
                modifiedStartColumn: m.modifiedRange.startColumn - 1,
                modifiedEndLineNumber: m.modifiedRange.endLineNumber - 1,
                modifiedEndColumn: m.modifiedRange.endColumn - 1
            }); })
        };
    });
}
exports.computeDiff = computeDiff;
var AceRange = require("../../../range").Range;
var DiffChunk = require("../base_diff_view").DiffChunk;
var DiffProvider = /** @class */ (function () {
    function DiffProvider() {
    }
    DiffProvider.prototype.compute = function (originalLines, modifiedLines, opts) {
        if (!opts)
            opts = {};
        if (!opts.maxComputationTimeMs)
            opts.maxComputationTimeMs = 500;
        var chunks = computeDiff(originalLines, modifiedLines, opts) || [];
        return chunks.map(function (c) { return new DiffChunk(new AceRange(c.origStart, 0, c.origEnd, 0), new AceRange(c.editStart, 0, c.editEnd, 0), c.charChanges); });
    };
    return DiffProvider;
}());
exports.DiffProvider = DiffProvider;

});

define("ace/ext/diff",["require","exports","module","ace/ext/diff/inline_diff_view","ace/ext/diff/split_diff_view","ace/ext/diff/providers/default"], function(require, exports, module){/**
 * ## Diff extension
 *
 * Provides side-by-side and inline diff view capabilities for comparing code differences between two versions.
 * Supports visual highlighting of additions, deletions, and modifications with customizable diff providers
 * and rendering options. Includes features for synchronized scrolling, line number alignment, and
 * various diff computation algorithms.
 *
 * **Components:**
 * - `InlineDiffView`: Single editor view showing changes inline with markers
 * - `SplitDiffView`: Side-by-side comparison view with two synchronized editors
 * - `DiffProvider`: Configurable algorithms for computing differences
 *
 * **Usage:**
 * ```javascript
 * const diffView = createDiffView({
 *   valueA: originalContent,
 *   valueB: modifiedContent,
 *   inline: false // or 'a'/'b' for inline view
 * });
 * ```
 *
 * @module
 */
var InlineDiffView = require("./diff/inline_diff_view").InlineDiffView;
var SplitDiffView = require("./diff/split_diff_view").SplitDiffView;
var DiffProvider = require("./diff/providers/default").DiffProvider;
function createDiffView(diffModel, options) {
    diffModel = diffModel || {};
    diffModel.diffProvider = diffModel.diffProvider || new DiffProvider(); //use default diff provider;
    var diffView;
    if (diffModel.inline) {
        diffView = new InlineDiffView(diffModel);
    }
    else {
        diffView = new SplitDiffView(diffModel);
    }
    if (options) {
        diffView.setOptions(options);
    }
    return diffView;
}
exports.InlineDiffView = InlineDiffView;
exports.SplitDiffView = SplitDiffView;
exports.DiffProvider = DiffProvider;
exports.createDiffView = createDiffView;

});                (function() {
                    window.require(["ace/ext/diff"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            