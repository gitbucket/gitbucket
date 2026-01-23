define("ace/ext/command_bar",["require","exports","module","ace/tooltip","ace/lib/event_emitter","ace/lib/lang","ace/lib/dom","ace/lib/oop","ace/lib/useragent"], function(require, exports, module){/**
 * ## Command Bar extension.
 *
 * Provides an interactive command bar tooltip that displays above the editor's active line. The extension enables
 * clickable commands with keyboard shortcuts, icons, and various button types including standard buttons, checkboxes,
 * and text elements. Supports overflow handling with a secondary tooltip for additional commands when space is limited.
 * The tooltip can be configured to always show or display only on mouse hover over the active line.
 *
 * @module
 */
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
var Tooltip = require("../tooltip").Tooltip;
var EventEmitter = require("../lib/event_emitter").EventEmitter;
var lang = require("../lib/lang");
var dom = require("../lib/dom");
var oop = require("../lib/oop");
var useragent = require("../lib/useragent");
var BUTTON_CLASS_NAME = 'command_bar_tooltip_button';
var VALUE_CLASS_NAME = 'command_bar_button_value';
var CAPTION_CLASS_NAME = 'command_bar_button_caption';
var KEYBINDING_CLASS_NAME = 'command_bar_keybinding';
var TOOLTIP_CLASS_NAME = 'command_bar_tooltip';
var MORE_OPTIONS_BUTTON_ID = 'MoreOptionsButton';
var defaultDelay = 100;
var defaultMaxElements = 4;
var minPosition = function (posA, posB) {
    if (posB.row > posA.row) {
        return posA;
    }
    else if (posB.row === posA.row && posB.column > posA.column) {
        return posA;
    }
    return posB;
};
var keyDisplayMap = {
    "Ctrl": { mac: "^" },
    "Option": { mac: "⌥" },
    "Command": { mac: "⌘" },
    "Cmd": { mac: "⌘" },
    "Shift": "⇧",
    "Left": "←",
    "Right": "→",
    "Up": "↑",
    "Down": "↓"
};
var CommandBarTooltip = /** @class */ (function () {
    function CommandBarTooltip(parentNode, options) {
        var e_1, _a;
        options = options || {};
        this.parentNode = parentNode;
        this.tooltip = new Tooltip(this.parentNode);
        this.moreOptions = new Tooltip(this.parentNode);
        this.maxElementsOnTooltip = options.maxElementsOnTooltip || defaultMaxElements;
        this.$alwaysShow = options.alwaysShow || false;
        this.eventListeners = {};
        this.elements = {};
        this.commands = {};
        this.tooltipEl = dom.buildDom(['div', { class: TOOLTIP_CLASS_NAME }], this.tooltip.getElement());
        this.moreOptionsEl = dom.buildDom(['div', { class: TOOLTIP_CLASS_NAME + " tooltip_more_options" }], this.moreOptions.getElement());
        this.$showTooltipTimer = lang.delayedCall(this.$showTooltip.bind(this), options.showDelay || defaultDelay);
        this.$hideTooltipTimer = lang.delayedCall(this.$hideTooltip.bind(this), options.hideDelay || defaultDelay);
        this.$tooltipEnter = this.$tooltipEnter.bind(this);
        this.$onMouseMove = this.$onMouseMove.bind(this);
        this.$onChangeScroll = this.$onChangeScroll.bind(this);
        this.$onEditorChangeSession = this.$onEditorChangeSession.bind(this);
        this.$scheduleTooltipForHide = this.$scheduleTooltipForHide.bind(this);
        this.$preventMouseEvent = this.$preventMouseEvent.bind(this);
        try {
            for (var _b = __values(["mousedown", "mouseup", "click"]), _c = _b.next(); !_c.done; _c = _b.next()) {
                var event = _c.value;
                this.tooltip.getElement().addEventListener(event, this.$preventMouseEvent);
                this.moreOptions.getElement().addEventListener(event, this.$preventMouseEvent);
            }
        }
        catch (e_1_1) { e_1 = { error: e_1_1 }; }
        finally {
            try {
                if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
            }
            finally { if (e_1) throw e_1.error; }
        }
    }
    CommandBarTooltip.prototype.registerCommand = function (id, command) {
        var registerForMainTooltip = Object.keys(this.commands).length < this.maxElementsOnTooltip;
        if (!registerForMainTooltip && !this.elements[MORE_OPTIONS_BUTTON_ID]) {
            this.$createCommand(MORE_OPTIONS_BUTTON_ID, {
                name: "···",
                exec: 
                function () {
                    this.$shouldHideMoreOptions = false;
                    this.$setMoreOptionsVisibility(!this.isMoreOptionsShown());
                }.bind(this),
                type: "checkbox",
                getValue: function () {
                    return this.isMoreOptionsShown();
                }.bind(this),
                enabled: true
            }, true);
        }
        this.$createCommand(id, command, registerForMainTooltip);
        if (this.isShown()) {
            this.updatePosition();
        }
    };
    CommandBarTooltip.prototype.isShown = function () {
        return !!this.tooltip && this.tooltip.isOpen;
    };
    CommandBarTooltip.prototype.isMoreOptionsShown = function () {
        return !!this.moreOptions && this.moreOptions.isOpen;
    };
    CommandBarTooltip.prototype.getAlwaysShow = function () {
        return this.$alwaysShow;
    };
    CommandBarTooltip.prototype.setAlwaysShow = function (alwaysShow) {
        this.$alwaysShow = alwaysShow;
        this.$updateOnHoverHandlers(!this.$alwaysShow);
        this._signal("alwaysShow", this.$alwaysShow);
    };
    CommandBarTooltip.prototype.attach = function (editor) {
        if (!editor || (this.isShown() && this.editor === editor)) {
            return;
        }
        this.detach();
        this.editor = editor;
        this.editor.on("changeSession", this.$onEditorChangeSession);
        if (this.editor.session) {
            this.editor.session.on("changeScrollLeft", this.$onChangeScroll);
            this.editor.session.on("changeScrollTop", this.$onChangeScroll);
        }
        if (this.getAlwaysShow()) {
            this.$showTooltip();
        }
        else {
            this.$updateOnHoverHandlers(true);
        }
    };
    CommandBarTooltip.prototype.updatePosition = function () {
        if (!this.editor) {
            return;
        }
        var renderer = this.editor.renderer;
        var ranges;
        if (this.editor.selection.getAllRanges) {
            ranges = this.editor.selection.getAllRanges();
        }
        else {
            ranges = [this.editor.getSelectionRange()];
        }
        if (!ranges.length) {
            return;
        }
        var minPos = minPosition(ranges[0].start, ranges[0].end);
        for (var i = 0, range; range = ranges[i]; i++) {
            minPos = minPosition(minPos, minPosition(range.start, range.end));
        }
        var pos = renderer.$cursorLayer.getPixelPosition(minPos, true);
        var tooltipEl = this.tooltip.getElement();
        var screenWidth = window.innerWidth;
        var screenHeight = window.innerHeight;
        var rect = this.editor.container.getBoundingClientRect();
        pos.top += rect.top - renderer.layerConfig.offset;
        pos.left += rect.left + renderer.gutterWidth - renderer.scrollLeft;
        var cursorVisible = pos.top >= rect.top && pos.top <= rect.bottom &&
            pos.left >= rect.left + renderer.gutterWidth && pos.left <= rect.right;
        if (!cursorVisible && this.isShown()) {
            this.$hideTooltip();
            return;
        }
        else if (cursorVisible && !this.isShown() && this.getAlwaysShow()) {
            this.$showTooltip();
            return;
        }
        var top = pos.top - tooltipEl.offsetHeight;
        var left = Math.min(screenWidth - tooltipEl.offsetWidth, pos.left);
        var tooltipFits = top >= 0 && top + tooltipEl.offsetHeight <= screenHeight &&
            left >= 0 && left + tooltipEl.offsetWidth <= screenWidth;
        if (!tooltipFits) {
            this.$hideTooltip();
            return;
        }
        this.tooltip.setPosition(left, top);
        if (this.isMoreOptionsShown()) {
            top = top + tooltipEl.offsetHeight;
            left = this.elements[MORE_OPTIONS_BUTTON_ID].getBoundingClientRect().left;
            var moreOptionsEl = this.moreOptions.getElement();
            var screenHeight = window.innerHeight;
            if (top + moreOptionsEl.offsetHeight > screenHeight) {
                top -= tooltipEl.offsetHeight + moreOptionsEl.offsetHeight;
            }
            if (left + moreOptionsEl.offsetWidth > screenWidth) {
                left = screenWidth - moreOptionsEl.offsetWidth;
            }
            this.moreOptions.setPosition(left, top);
        }
    };
    CommandBarTooltip.prototype.update = function () {
        Object.keys(this.elements).forEach(this.$updateElement.bind(this));
    };
    CommandBarTooltip.prototype.detach = function () {
        this.tooltip.hide();
        this.moreOptions.hide();
        this.$updateOnHoverHandlers(false);
        if (this.editor) {
            this.editor.off("changeSession", this.$onEditorChangeSession);
            if (this.editor.session) {
                this.editor.session.off("changeScrollLeft", this.$onChangeScroll);
                this.editor.session.off("changeScrollTop", this.$onChangeScroll);
            }
        }
        this.$mouseInTooltip = false;
        this.editor = null;
    };
    CommandBarTooltip.prototype.destroy = function () {
        if (this.tooltip && this.moreOptions) {
            this.detach();
            this.tooltip.destroy();
            this.moreOptions.destroy();
        }
        this.eventListeners = {};
        this.commands = {};
        this.elements = {};
        this.tooltip = this.moreOptions = this.parentNode = null;
    };
    CommandBarTooltip.prototype.$createCommand = function (id, command, forMainTooltip) {
        var parentEl = forMainTooltip ? this.tooltipEl : this.moreOptionsEl;
        var keyParts = [];
        var bindKey = command.bindKey;
        if (bindKey) {
            if (typeof bindKey === 'object') {
                bindKey = useragent.isMac ? bindKey.mac : bindKey.win;
            }
            bindKey = bindKey.split("|")[0];
            keyParts = bindKey.split("-");
            keyParts = keyParts.map(function (key) {
                if (keyDisplayMap[key]) {
                    if (typeof keyDisplayMap[key] === 'string') {
                        return keyDisplayMap[key];
                    }
                    else if (useragent.isMac && keyDisplayMap[key].mac) {
                        return keyDisplayMap[key].mac;
                    }
                }
                return key;
            });
        }
        var buttonNode;
        if (forMainTooltip && command.iconCssClass) {
            buttonNode = [
                'div',
                {
                    class: ["ace_icon_svg", command.iconCssClass].join(" "),
                    "aria-label": command.name + " (" + command.bindKey + ")"
                }
            ];
        }
        else {
            buttonNode = [
                ['div', { class: VALUE_CLASS_NAME }],
                ['div', { class: CAPTION_CLASS_NAME }, command.name]
            ];
            if (keyParts.length) {
                buttonNode.push([
                    'div',
                    { class: KEYBINDING_CLASS_NAME },
                    keyParts.map(function (keyPart) {
                        return ['div', keyPart];
                    })
                ]);
            }
        }
        dom.buildDom(['div', { class: [BUTTON_CLASS_NAME, command.cssClass || ""].join(" "), ref: id }, buttonNode], parentEl, this.elements);
        this.commands[id] = command;
        var eventListener = 
        function (e) {
            if (this.editor) {
                this.editor.focus();
            }
            this.$shouldHideMoreOptions = this.isMoreOptionsShown();
            if (!this.elements[id].disabled && command.exec) {
                command.exec(this.editor);
            }
            if (this.$shouldHideMoreOptions) {
                this.$setMoreOptionsVisibility(false);
            }
            this.update();
            e.preventDefault();
        }.bind(this);
        this.eventListeners[id] = eventListener;
        this.elements[id].addEventListener('click', eventListener.bind(this));
        this.$updateElement(id);
    };
    CommandBarTooltip.prototype.$setMoreOptionsVisibility = function (visible) {
        if (visible) {
            this.moreOptions.setTheme(this.editor.renderer.theme);
            this.moreOptions.setClassName(TOOLTIP_CLASS_NAME + "_wrapper");
            this.moreOptions.show();
            this.update();
            this.updatePosition();
        }
        else {
            this.moreOptions.hide();
        }
    };
    CommandBarTooltip.prototype.$onEditorChangeSession = function (e) {
        if (e.oldSession) {
            e.oldSession.off("changeScrollTop", this.$onChangeScroll);
            e.oldSession.off("changeScrollLeft", this.$onChangeScroll);
        }
        this.detach();
    };
    CommandBarTooltip.prototype.$onChangeScroll = function () {
        if (this.editor.renderer && (this.isShown() || this.getAlwaysShow())) {
            this.editor.renderer.once("afterRender", this.updatePosition.bind(this));
        }
    };
    CommandBarTooltip.prototype.$onMouseMove = function (e) {
        if (this.$mouseInTooltip) {
            return;
        }
        var cursorPosition = this.editor.getCursorPosition();
        var cursorScreenPosition = this.editor.renderer.textToScreenCoordinates(cursorPosition.row, cursorPosition.column);
        var lineHeight = this.editor.renderer.lineHeight;
        var isInCurrentLine = e.clientY >= cursorScreenPosition.pageY && e.clientY < cursorScreenPosition.pageY + lineHeight;
        if (isInCurrentLine) {
            if (!this.isShown() && !this.$showTooltipTimer.isPending()) {
                this.$showTooltipTimer.delay();
            }
            if (this.$hideTooltipTimer.isPending()) {
                this.$hideTooltipTimer.cancel();
            }
        }
        else {
            if (this.isShown() && !this.$hideTooltipTimer.isPending()) {
                this.$hideTooltipTimer.delay();
            }
            if (this.$showTooltipTimer.isPending()) {
                this.$showTooltipTimer.cancel();
            }
        }
    };
    CommandBarTooltip.prototype.$preventMouseEvent = function (e) {
        if (this.editor) {
            this.editor.focus();
        }
        e.preventDefault();
    };
    CommandBarTooltip.prototype.$scheduleTooltipForHide = function () {
        this.$mouseInTooltip = false;
        this.$showTooltipTimer.cancel();
        this.$hideTooltipTimer.delay();
    };
    CommandBarTooltip.prototype.$tooltipEnter = function () {
        this.$mouseInTooltip = true;
        if (this.$showTooltipTimer.isPending()) {
            this.$showTooltipTimer.cancel();
        }
        if (this.$hideTooltipTimer.isPending()) {
            this.$hideTooltipTimer.cancel();
        }
    };
    CommandBarTooltip.prototype.$updateOnHoverHandlers = function (enableHover) {
        var tooltipEl = this.tooltip.getElement();
        var moreOptionsEl = this.moreOptions.getElement();
        if (enableHover) {
            if (this.editor) {
                this.editor.on("mousemove", this.$onMouseMove);
                this.editor.renderer.getMouseEventTarget().addEventListener("mouseout", this.$scheduleTooltipForHide, true);
            }
            tooltipEl.addEventListener('mouseenter', this.$tooltipEnter);
            tooltipEl.addEventListener('mouseleave', this.$scheduleTooltipForHide);
            moreOptionsEl.addEventListener('mouseenter', this.$tooltipEnter);
            moreOptionsEl.addEventListener('mouseleave', this.$scheduleTooltipForHide);
        }
        else {
            if (this.editor) {
                this.editor.off("mousemove", this.$onMouseMove);
                this.editor.renderer.getMouseEventTarget().removeEventListener("mouseout", this.$scheduleTooltipForHide, true);
            }
            tooltipEl.removeEventListener('mouseenter', this.$tooltipEnter);
            tooltipEl.removeEventListener('mouseleave', this.$scheduleTooltipForHide);
            moreOptionsEl.removeEventListener('mouseenter', this.$tooltipEnter);
            moreOptionsEl.removeEventListener('mouseleave', this.$scheduleTooltipForHide);
        }
    };
    CommandBarTooltip.prototype.$showTooltip = function () {
        if (this.isShown()) {
            return;
        }
        this.tooltip.setTheme(this.editor.renderer.theme);
        this.tooltip.setClassName(TOOLTIP_CLASS_NAME + "_wrapper");
        this.tooltip.show();
        this.update();
        this.updatePosition();
        this._signal("show");
    };
    CommandBarTooltip.prototype.$hideTooltip = function () {
        this.$mouseInTooltip = false;
        if (!this.isShown()) {
            return;
        }
        this.moreOptions.hide();
        this.tooltip.hide();
        this._signal("hide");
    };
    CommandBarTooltip.prototype.$updateElement = function (id) {
        var command = this.commands[id];
        if (!command) {
            return;
        }
        var el = this.elements[id];
        var commandEnabled = command.enabled;
        if (typeof commandEnabled === 'function') {
            commandEnabled = commandEnabled(this.editor);
        }
        if (typeof command.getValue === 'function') {
            var value = command.getValue(this.editor);
            if (command.type === 'text') {
                el.textContent = value;
            }
            else if (command.type === 'checkbox') {
                var domCssFn = value ? dom.addCssClass : dom.removeCssClass;
                var isOnTooltip = el.parentElement === this.tooltipEl;
                el.ariaChecked = value;
                if (isOnTooltip) {
                    domCssFn(el, "ace_selected");
                }
                else {
                    el = el.querySelector("." + VALUE_CLASS_NAME);
                    domCssFn(el, "ace_checkmark");
                }
            }
        }
        if (commandEnabled && el.disabled) {
            dom.removeCssClass(el, "ace_disabled");
            el.ariaDisabled = el.disabled = false;
            el.removeAttribute("disabled");
        }
        else if (!commandEnabled && !el.disabled) {
            dom.addCssClass(el, "ace_disabled");
            el.ariaDisabled = el.disabled = true;
            el.setAttribute("disabled", "");
        }
    };
    return CommandBarTooltip;
}());
oop.implement(CommandBarTooltip.prototype, EventEmitter);
dom.importCssString("\n.ace_tooltip.".concat(TOOLTIP_CLASS_NAME, "_wrapper {\n    padding: 0;\n}\n\n.ace_tooltip .").concat(TOOLTIP_CLASS_NAME, " {\n    padding: 1px 5px;\n    display: flex;\n    pointer-events: auto;\n}\n\n.ace_tooltip .").concat(TOOLTIP_CLASS_NAME, ".tooltip_more_options {\n    padding: 1px;\n    flex-direction: column;\n}\n\ndiv.").concat(BUTTON_CLASS_NAME, " {\n    display: inline-flex;\n    cursor: pointer;\n    margin: 1px;\n    border-radius: 2px;\n    padding: 2px 5px;\n    align-items: center;\n}\n\ndiv.").concat(BUTTON_CLASS_NAME, ".ace_selected,\ndiv.").concat(BUTTON_CLASS_NAME, ":hover:not(.ace_disabled) {\n    background-color: rgba(0, 0, 0, 0.1);\n}\n\ndiv.").concat(BUTTON_CLASS_NAME, ".ace_disabled {\n    color: #777;\n    pointer-events: none;\n}\n\ndiv.").concat(BUTTON_CLASS_NAME, " .ace_icon_svg {\n    height: 12px;\n    background-color: #000;\n}\n\ndiv.").concat(BUTTON_CLASS_NAME, ".ace_disabled .ace_icon_svg {\n    background-color: #777;\n}\n\n.").concat(TOOLTIP_CLASS_NAME, ".tooltip_more_options .").concat(BUTTON_CLASS_NAME, " {\n    display: flex;\n}\n\n.").concat(TOOLTIP_CLASS_NAME, ".").concat(VALUE_CLASS_NAME, " {\n    display: none;\n}\n\n.").concat(TOOLTIP_CLASS_NAME, ".tooltip_more_options .").concat(VALUE_CLASS_NAME, " {\n    display: inline-block;\n    width: 12px;\n}\n\n.").concat(CAPTION_CLASS_NAME, " {\n    display: inline-block;\n}\n\n.").concat(KEYBINDING_CLASS_NAME, " {\n    margin: 0 2px;\n    display: inline-block;\n    font-size: 8px;\n}\n\n.").concat(TOOLTIP_CLASS_NAME, ".tooltip_more_options .").concat(KEYBINDING_CLASS_NAME, " {\n    margin-left: auto;\n}\n\n.").concat(KEYBINDING_CLASS_NAME, " div {\n    display: inline-block;\n    min-width: 8px;\n    padding: 2px;\n    margin: 0 1px;\n    border-radius: 2px;\n    background-color: #ccc;\n    text-align: center;\n}\n\n.ace_dark.ace_tooltip .").concat(TOOLTIP_CLASS_NAME, " {\n    background-color: #373737;\n    color: #eee;\n}\n\n.ace_dark div.").concat(BUTTON_CLASS_NAME, ".ace_disabled {\n    color: #979797;\n}\n\n.ace_dark div.").concat(BUTTON_CLASS_NAME, ".ace_selected,\n.ace_dark div.").concat(BUTTON_CLASS_NAME, ":hover:not(.ace_disabled) {\n    background-color: rgba(255, 255, 255, 0.1);\n}\n\n.ace_dark div.").concat(BUTTON_CLASS_NAME, " .ace_icon_svg {\n    background-color: #eee;\n}\n\n.ace_dark div.").concat(BUTTON_CLASS_NAME, ".ace_disabled .ace_icon_svg {\n    background-color: #979797;\n}\n\n.ace_dark .").concat(BUTTON_CLASS_NAME, ".ace_disabled {\n    color: #979797;\n}\n\n.ace_dark .").concat(KEYBINDING_CLASS_NAME, " div {\n    background-color: #575757;\n}\n\n.ace_checkmark::before {\n    content: '\u2713';\n}\n"), "commandbar.css", false);
exports.CommandBarTooltip = CommandBarTooltip;
exports.TOOLTIP_CLASS_NAME = TOOLTIP_CLASS_NAME;
exports.BUTTON_CLASS_NAME = BUTTON_CLASS_NAME;

});                (function() {
                    window.require(["ace/ext/command_bar"], function(m) {
                        if (typeof module == "object" && typeof exports == "object" && module) {
                            module.exports = m;
                        }
                    });
                })();
            