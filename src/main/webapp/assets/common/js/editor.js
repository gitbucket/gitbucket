(function () {
    document.addEventListener('DOMContentLoaded', () => {
        /**
         * element to attach images or documents by dragging & dropping, or selecting them.
         * @type {Element}
         */
        const clickable = document.querySelector(".clickable");

        /**
         * A function to update the file mode of the Ace editor
         */
        const updateFileMode = function () {
            if (gitbucket.getFileName() != "") {
                var modelist = ace.require("ace/ext/modelist");
                var mode = modelist.getModeForPath(gitbucket.getFileName());
                gitbucket.editor.getSession().setMode(mode.mode);
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