(function () {
    document.addEventListener('DOMContentLoaded', () => {
        /**
         * element to attach images or documents by dragging & dropping, or selecting them.
         * @type {Element}
         */
        const clickable = document.querySelector(".clickable");

        /**
         * element of markdown formatting toolbar buttons
         * @type {Element}
         */
        // inline format
        const markdownToolBar = document.getElementById("markdown-toolbar");
        const markdownBold = document.getElementById("markdown-bold");
        const markdownItalic = document.getElementById("markdown-italic");
        const markdownCode = document.getElementById("markdown-code");
        const markdownLink = document.getElementById("markdown-link");
        // block level format
        const markdownHeading = document.getElementById("markdown-heading");
        const markdownQuote = document.getElementById("markdown-quote");
        const markdownListUl = document.getElementById("markdown-list-ul");
        const markdownListOl = document.getElementById("markdown-list-ol");
        const markdownTask = document.getElementById("markdown-task");
        const markdownCodeBlock = document.getElementById("markdown-code-block");

        /**
         * constants for line type
         */
        const headingPattern = /^#{1,6} /;
        const quotePattern = /^((>+ )+|>+ )/;
        const nestedQuotePattern = /^(> ?){2,}/;
        const taskPattern = /^ *- \[ \] /;
        const doneTaskPattern = /^ *- \[x\] /;
        const ulPattern = /^ *- /;
        const olPattern = /^ *[0-9]. /;

        const TYPE_NONE = 0;
        const TYPE_HEADING = 1;
        const TYPE_QUOTE = 2;
        const TYPE_TASK = 3;
        const TYPE_DONE_TASK = 4;
        const TYPE_UL = 5;
        const TYPE_OL = 6;

        /**
         * Determine the type of line
         *
         * @param {*} line line of editor contents
         * @returns [TYPE, match]
         */
        const getLineType = function (line) {
            if (m = line.match(headingPattern)) {
                return [TYPE_HEADING, m];
            } else if (m = line.match(quotePattern)) {
                return [TYPE_QUOTE, m];
            } else if (m = line.match(taskPattern)) {
                return [TYPE_TASK, m];
            } else if (m = line.match(doneTaskPattern)) {
                return [TYPE_DONE_TASK, m];
            } else if (m = line.match(ulPattern)) {
                return [TYPE_UL, m];
            } else if (m = line.match(olPattern)) {
                return [TYPE_OL, m];
            } else {
                return [TYPE_NONE, null];
            }
        };

        /**
         * Event Handler to capture markdown bold button clicks
         */
        markdownBold.addEventListener('click', (e) => {
            var selectedText = gitbucket.editor.getSelectedText();
            if (selectedText.length > 0) {
                var boldText = "**" + selectedText + "**";
                gitbucket.editor.insert(boldText);
            }
            markdownBold.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown italic button clicks
         */
        markdownItalic.addEventListener('click', (e) => {
            var selectedText = gitbucket.editor.getSelectedText();
            if (selectedText.length > 0) {
                var italicText = "*" + selectedText + "*";
                gitbucket.editor.insert(italicText);
            }
            markdownItalic.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown code button clicks
         */
        markdownCode.addEventListener('click', (e) => {
            var selectedText = gitbucket.editor.getSelectedText();
            if (selectedText.length > 0) {
                var codeText = "`" + selectedText + "`";
                gitbucket.editor.insert(codeText);
            }
            markdownCode.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown link button clicks
         */
        markdownLink.addEventListener('click', (e) => {
            var linkText = "[" + gitbucket.editor.getSelectedText() + "]()";
            gitbucket.editor.insert(linkText);
            markdownLink.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown heading button clicks
         */
        markdownHeading.addEventListener('click', (e) => {
            var range = gitbucket.editor.getSelectionRange();
            for (row = range.start.row; row <= range.end.row; row++) {
                gitbucket.editor.clearSelection();
                gitbucket.editor.selection.moveCursorTo(row, 0);
                gitbucket.editor.selection.selectLineEnd();
                var selectedText = gitbucket.editor.getSelectedText();
                var lineType = getLineType(selectedText);
                switch(lineType[0]) {
                    case TYPE_HEADING:
                        var level = selectedText.indexOf(' ');
                        if (level < 6) {
                            var headingText = "#" + selectedText;
                            gitbucket.editor.insert(headingText);
                        } else {
                            var headingText = selectedText.replace("###### ","");
                            gitbucket.editor.insert(headingText);
                        }
                        break;
                    case TYPE_NONE:
                        var headingText = "# " + selectedText;
                        gitbucket.editor.insert(headingText);
                        break;
                    default:
                        var headingText = selectedText.replace(lineType[1][0], "# ");
                        gitbucket.editor.insert(headingText);
                }
            }
            markdownHeading.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown quote button clicks
         */
        markdownQuote.addEventListener('click', (e) => {
            var range = gitbucket.editor.getSelectionRange();
            for (row = range.start.row; row <= range.end.row; row++) {
                gitbucket.editor.clearSelection();
                gitbucket.editor.selection.moveCursorTo(row, 0);
                gitbucket.editor.selection.selectLineEnd();
                var selectedText = gitbucket.editor.getSelectedText();
                var lineType = getLineType(selectedText);
                switch(lineType[0]) {
                    case TYPE_QUOTE:
                        if (m = selectedText.match(nestedQuotePattern)) {
                            var unquoteText = selectedText.replace(m[0], "");
                            gitbucket.editor.insert(unquoteText);
                        } else {
                            var quoteText = "> " + selectedText;
                            gitbucket.editor.insert(quoteText);
                        }
                        break;
                    case TYPE_NONE:
                        var quoteText = "> " + selectedText;
                        gitbucket.editor.insert(quoteText);
                        break;
                    default:
                        var quoteText = selectedText.replace(lineType[1][0], "> ");
                        gitbucket.editor.insert(quoteText);
                }
            }
            markdownQuote.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown unordered list button clicks
         */
        markdownListUl.addEventListener('click', (e) => {
            var range = gitbucket.editor.getSelectionRange();
            for (row = range.start.row; row <= range.end.row; row++) {
                gitbucket.editor.clearSelection();
                gitbucket.editor.selection.moveCursorTo(row, 0);
                gitbucket.editor.selection.selectLineEnd();
                var selectedText = gitbucket.editor.getSelectedText();
                var lineType = getLineType(selectedText);
                switch(lineType[0]) {
                    case TYPE_UL:
                        var unorderedList = selectedText.replace(lineType[1][0], "");
                        gitbucket.editor.insert(unorderedList);
                        break;
                    case TYPE_NONE:
                        var unorderedList = "- " + selectedText;
                        gitbucket.editor.insert(unorderedList);
                        break;
                    default:
                        var unorderedList = selectedText.replace(lineType[1][0], "- ");
                        gitbucket.editor.insert(unorderedList);
                }
            }
            markdownListUl.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown ordered list button clicks
         */
        markdownListOl.addEventListener('click', (e) => {
            var range = gitbucket.editor.getSelectionRange();
            for (row = range.start.row; row <= range.end.row; row++) {
                gitbucket.editor.clearSelection();
                gitbucket.editor.selection.moveCursorTo(row, 0);
                gitbucket.editor.selection.selectLineEnd();
                var selectedText = gitbucket.editor.getSelectedText();
                var lineType = getLineType(selectedText);
                switch(lineType[0]) {
                    case TYPE_OL:
                        var orderedList = selectedText.replace(lineType[1][0], "");
                        gitbucket.editor.insert(orderedList);
                        break;
                    case TYPE_NONE:
                        var orderedList = "1. " + selectedText;
                        gitbucket.editor.insert(orderedList);
                        break;
                    default:
                        var orderedList = selectedText.replace(lineType[1][0], "1. ");
                        gitbucket.editor.insert(orderedList);
                }
            }
            markdownListOl.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown task button clicks
         */
        markdownTask.addEventListener('click', (e) => {
            var range = gitbucket.editor.getSelectionRange();
            for (row = range.start.row; row <= range.end.row; row++) {
                gitbucket.editor.clearSelection();
                gitbucket.editor.selection.moveCursorTo(row, 0);
                gitbucket.editor.selection.selectLineEnd();
                var selectedText = gitbucket.editor.getSelectedText();
                var lineType = getLineType(selectedText);
                var taskList = "";
                switch(lineType[0]) {
                    case TYPE_DONE_TASK:
                        taskList = selectedText.replace("[x]", "[ ]");
                        break;
                    case TYPE_TASK:
                        taskList = selectedText.replace("[ ]", "[x]");
                        break;
                    case TYPE_NONE:
                        taskList = "- [ ] " + selectedText;
                        break;
                    default:
                        taskList = selectedText.replace(lineType[1][0], "- [ ] ");
                }
                gitbucket.editor.insert(taskList);
            }
            markdownTask.blur();
            gitbucket.editor.focus();
        });

        /**
         * Event Handler to capture markdown code block button
         */
        markdownCodeBlock.addEventListener('click', (e) => {
            var range = gitbucket.editor.getSelectionRange();
            gitbucket.editor.clearSelection();
            gitbucket.editor.selection.moveCursorTo(range.start.row, 0);
            gitbucket.editor.selection.selectLineEnd();
            gitbucket.editor.selection.moveCursorTo(range.end.row, 0);
            gitbucket.editor.selection.selectLineEnd();
            var codeBlockText = "```\n" + gitbucket.editor.getSelectedText() + "\n```";
            gitbucket.editor.insert(codeBlockText);
            gitbucket.editor.selection.moveCursorTo(range.start.row, 3);
            markdownCodeBlock.blur();
            gitbucket.editor.focus();
        });


        /**
         * A function to update the file mode of the Ace editor
         */
        const updateFileMode = function () {
            if (gitbucket.getFileName() != "") {
                var modelist = ace.require("ace/ext/modelist");
                var mode = modelist.getModeForPath(gitbucket.getFileName());
                gitbucket.editor.getSession().setMode(mode.mode);
                if (mode.name == "markdown") {
                    markdownToolBar.style.display = "block";
                } else {
                    markdownToolBar.style.display = "none";
                }
            } else {
                    markdownToolBar.style.display = "none";
            }
        };

        /**
         * A function to update status of commit button
         */
        const updateCommitButtonStatus = function () {
            if (gitbucket.editor.getValue() == $('#initial').val() && $('#newFileName').val() == $('#oldFileName').val()) {
                $('#commitButton').attr('disabled', true);
            } else {
                $('#commitButton').attr('disabled', false);
            }
        }

        // Initialize the editor contents
        $('#editor').text($('#initial').val());

        // Initializing Ace editor
        gitbucket.editor = ace.edit("editor");

        // Initialize Ace editor keyboard handler
        var aceKeyboard = localStorage.getItem("gitbucket:editor:keyboard") || "";
        gitbucket.editor.setKeyboardHandler(aceKeyboard == "" ? null : aceKeyboard);

        var aceKeyboardSelect = document.getElementById("aceKeyboardSelect");
        aceKeyboardSelect.value = aceKeyboard;

        // Event handler to change the keyboard handler for the Ace editor
        aceKeyboardSelect.addEventListener('change', () => {
            gitbucket.editor.setKeyboardHandler(aceKeyboardSelect.value == "" ? null : aceKeyboardSelect.value);
            localStorage.setItem("gitbucket:editor:keyboard", aceKeyboardSelect.value);
        }, true)

        // Initialize the Ace editor theme
        if (typeof localStorage.getItem('gitbucket:editor:theme') == "string") {
            $('#theme').val(localStorage.getItem('gitbucket:editor:theme'));
        }

        gitbucket.editor.setTheme("ace/theme/" + $('#theme').val());

        // Event handler to change the Ace editor theme
        $('#theme').change(function () {
            gitbucket.editor.setTheme("ace/theme/" + $('#theme').val());
            localStorage.setItem('gitbucket:editor:theme', $('#theme').val());
        });

        // Initialize text wrapping for Ace editor
        if (localStorage.getItem('gitbucket:editor:wrap') == 'true') {
            gitbucket.editor.getSession().setUseWrapMode(true);
            $('#wrap').val('true');
        }

        // Event handler to change text wrapping for Ace editor
        $('#wrap').change(function () {
            if ($('#wrap option:selected').val() == 'true') {
                gitbucket.editor.getSession().setUseWrapMode(true);
                localStorage.setItem('gitbucket:editor:wrap', 'true');
            } else {
                gitbucket.editor.getSession().setUseWrapMode(false);
                localStorage.setItem('gitbucket:editor:wrap', 'false');
            }
        });

        // Initialize file mode for Ace editor
        updateFileMode();

        // Determine whether Ace Editor can be edited
        if (gitbucket.protected) {
            gitbucket.editor.setReadOnly(true);
        }

        // Initialize tabSize, newLineMode, and useSoftTabs for the Ace editor.
        gitbucket.editor.getSession().setOption("tabSize", gitbucket.tabSize);
        gitbucket.editor.getSession().setOption("newLineMode", gitbucket.newLineMode);
        gitbucket.editor.getSession().setOption("useSoftTabs", gitbucket.useSoftTabs);

        // Controls the activation of the commit button when using the repository file editor.
        if (!document.location.href.endsWith("/_edit")) {
            gitbucket.editor.on('change', function () {
                updateCommitButtonStatus();
            });

            $('#newFileName').watch(function () {
                updateCommitButtonStatus();
                updateFileMode();
            });
        }

        // When the Commit button is clicked, the form content will be overwritten with the editor content.
        $('#commitButton').click(function () {
            $('#content').val(gitbucket.editor.getValue());
        });

        // An event handler that defines what happens when the code button is clicked.
        $('#btn-code').click(function () {
            $('#editor').show();
            $('#preview').hide();
            $('#btn-preview').removeClass('active');
            if (clickable) clickable.style.display = "block";
        });

        // An event handler that defines what happens when the preview button is clicked.
        $('#btn-preview').click(function () {
            $('#editor').hide();
            $('#preview').show();
            $('#btn-code').removeClass('active');
            if (clickable) clickable.style.display = "none";

            // Determine if rendering is possible
            $.get(gitbucket.isRenderableUrl + gitbucket.getFileName(), function (data) {
                if (data === 'true') {
                    // update preview
                    $('#preview').html(gitbucket.previewTemplate);
                    $.post(gitbucket.previewUrl, {
                        content: gitbucket.editor.getValue(),
                        enableWikiLink: gitbucket.enableWikiLink,
                        filename: gitbucket.getFilePath(),
                        enableRefsLink: false,
                        enableLineBreaks: false,
                        enableTaskList: false
                    }, function (data) {
                        $('#preview').empty().append(
                            $('<div class="markdown-body" style="padding-left: 20px; padding-right: 20px;">').html(data));
                        prettyPrint();
                    });
                } else {
                    // Show diff
                    $('#preview').empty()
                        .append($('<div id="diffText">'))
                        .append($('<textarea id="newText" style="display: none;">').data('file-name', $("#newFileName").val()).data('val', gitbucket.editor.getValue()))
                        .append($('<textarea id="oldText" style="display: none;">').data('file-name', $("#oldFileName").val()).data('val', $('#initial').val()));
                    diffUsingJS('oldText', 'newText', 'diffText', 1);
                }
            });
        });

        // In the case of a Wiki editor, it controls the parts for attachments and delete button.
        if (document.location.href.endsWith("/_edit")) {
            try {
                // Event handler to capture drag and drop into the editor
                $('#editor').dropzone({
                    url: gitbucket.uploadUrl,
                    maxFilesize: gitbucket.maxFilesize,
                    timeout: gitbucket.timeout,
                    clickable: false,
                    previewTemplate: "<div class=\"dz-preview\">\n  <div class=\"dz-progress\"><span class=\"dz-upload\" data-dz-uploadprogress>Uploading your files...</span></div>\n  <div class=\"dz-error-message\"><span data-dz-errormessage></span></div>\n</div>",
                    success: function (file, id) {
                        var attachFile = (file.type.match(/image\/.*/) ? '\n![' + file.name.split('.')[0] : '\n[' + file.name) + '](' + file.name + ')';
                        gitbucket.editor.session.insert(gitbucket.editor.selection.getCursor(), attachFile);
                        $(file.previewElement).prevAll('div.dz-preview').addBack().remove();
                    }
                });

                // Event handler to capture drag and drop into the clickable
                $('.clickable').dropzone({
                    url: gitbucket.uploadUrl,
                    maxFilesize: gitbucket.maxFilesize,
                    timeout: gitbucket.timeout,
                    previewTemplate: "<div class=\"dz-preview\">\n  <div class=\"dz-progress\"><span class=\"dz-upload\" data-dz-uploadprogress>Uploading your files...</span></div>\n  <div class=\"dz-error-message\"><span data-dz-errormessage></span></div>\n</div>",
                    success: function (file, id) {
                        var attachFile = (file.type.match(/image\/.*/) ? '\n![' + file.name.split('.')[0] : '\n[' + file.name) + '](' + file.name + ')';
                        gitbucket.editor.session.insert(gitbucket.editor.selection.getCursor(), attachFile);
                        $(file.previewElement).prevAll('div.dz-preview').addBack().remove();
                    }
                });
            } catch (e) {
                if (e.message !== "Dropzone already attached.") {
                    throw e;
                }
            }

            // An event handler to capture clicks on the Delete button
            $('#delete').click(function () {
                return confirm('Are you sure you want to delete this page?');
            });
        }
    });
})();