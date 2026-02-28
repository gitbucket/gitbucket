(function(window) {
    function strictNormalizeClassName(rawClassName) {
        return rawClassName
            .trim() // Remove the previous and subsequent blanks
            .toLowerCase() // Unification in lowercase letters
            .replace(/\s+/g, '-') // Replace whitespace with hyphens
            .replace(/[^a-z0-9\-_]/g, '_'); // Replace characters other than alphanumeric characters, hyphens, and underscores with underscores
    }

    if (typeof(emoji) === "object") {
        window.emojiCompleter = {
            triggerCharacters: [':'],
            getCompletions: function (editor, session, pos, prefix, callback) {
                if (session.$mode && session.$mode.$id === 'ace/mode/markdown') {
                    callback(null, emoji.map(function (table) {
                        var token = session.getTokenAt(pos.row, pos.column);
                        return {
                            caption: table.description,
                            value: token ? table.name.replace(token.value, "") : table.name,
                            className: strictNormalizeClassName(table.description),
                            meta: 'Emoji'
                        };
                    }));
                }
            },
            id: "emojiCompleter"
        };
    }
})(window);