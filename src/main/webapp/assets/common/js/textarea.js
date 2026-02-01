(function () {
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
   * Wraps the textarea selection with the specified string
   * 
   * @param {Element} textarea Target textarea element
   * @param {string} prefix The string to insert before the selection
   * @param {string} suffix  The string to insert after the selection
   * @param {boolean} allowNoSelectRange Allow insert even if not selected
   */
  window.surroundSelection = function (textarea, prefix, suffix, allowNoSelectRange) {
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = textarea.value.substring(start, end);
    if (!(selectedText.length == 0 && allowNoSelectRange == false)) {
      textarea.value = textarea.value.substring(0, start) + prefix + selectedText + suffix + textarea.value.substring(end);
      const newPosition = start + prefix.length + selectedText.length + suffix.length + (allowNoSelectRange ? -1 : 0);
      textarea.setSelectionRange(newPosition, newPosition);
    }
    textarea.focus();
  };

  /**
   * Extend selection to entireLine in textarea
   * 
   * @param {Element} textarea Target textarea element
   */
  window.extendSelectionToEntireLine = function (textarea) {
    const text = textarea.value;
    // Forward line break position (if not found it is -1, so add +1 to make it 0th character)
    const start = text.lastIndexOf('\n', textarea.selectionStart - 1) + 1;
    // Rear line break position (if not found, use end of string)
    let end = text.indexOf('\n', textarea.selectionEnd);
    if (end === -1) end = text.length;
    // Update selection to entire row
    textarea.setSelectionRange(start, end);
  };

  /**
   * Make the textarea selection a heading element
   * 
   * @param {Element} textarea 
   */
  window.setHeadding = function (textarea) {
    extendSelectionToEntireLine(textarea);
    var newText = "";
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = textarea.value.substring(start, end);
    selectedText.split('\n').forEach((line) => {
      var headingText = "";
      var lineType = getLineType(line);
      switch(lineType[0]) {
        case TYPE_HEADING:
          var level = selectedText.indexOf(' ');
          if (level < 6) {
              headingText = "#" + line;
          } else {
              headingText = line.replace("###### ","");
          }
          break;
        case TYPE_NONE:
          headingText = "# " + line;
          break;
        default:
          headingText = line.replace(lineType[1][0], "# ");
      }
      newText = newText + headingText + "\n";
    });
    textarea.value = textarea.value.substring(0, start) + newText.trimEnd() + textarea.value.substring(end);
    textarea.setSelectionRange(start, start + newText.trimEnd().length);
    textarea.focus();
  };

  /**
   * Make the textarea selection a quote element
   * 
   * @param {Element} textarea 
   */
  window.setQuote = function (textarea) {
    extendSelectionToEntireLine(textarea);
    var newText = "";
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = textarea.value.substring(start, end);
    selectedText.split('\n').forEach((line) => {
      var quoteText = "";
      var lineType = getLineType(line);
      switch(lineType[0]) {
        case TYPE_QUOTE:
          if (m = line.match(nestedQuotePattern)) {
            quoteText = line.replace(m[0], "");
          } else {
            quoteText = "> " + line;
          }
          break;
        case TYPE_NONE:
          quoteText = "> " + line;
          break;
        default:
          quoteText = line.replace(lineType[1][0], "> ");
      }
      newText = newText + quoteText + "\n";
    });
    textarea.value = textarea.value.substring(0, start) + newText.trimEnd() + textarea.value.substring(end);
    textarea.setSelectionRange(start, start + newText.trimEnd().length);
    textarea.focus();
  };

  /**
   * Make the textarea selection a unordered list element
   * 
   * @param {Element} textarea 
   */
  window.setUnorderedList = function (textarea) {
    extendSelectionToEntireLine(textarea);
    var newText = "";
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = textarea.value.substring(start, end);
    selectedText.split('\n').forEach((line) => {
      var unorderedList = "";
      var lineType = getLineType(selectedText);
      switch(lineType[0]) {
        case TYPE_UL:
          unorderedList = line.replace(lineType[1][0], "");
          break;
        case TYPE_NONE:
          unorderedList = "- " + line;
          break;
        default:
          unorderedList = line.replace(lineType[1][0], "- ");
      }
      newText = newText + unorderedList + "\n";
    });
    textarea.value = textarea.value.substring(0, start) + newText.trimEnd() + textarea.value.substring(end);
    textarea.setSelectionRange(start, start + newText.trimEnd().length);
    textarea.focus();
  };

  /**
   * Make the textarea selection a ordered list element
   * 
   * @param {Element} textarea 
   */
  window.setOrderedList = function (textarea) {
    extendSelectionToEntireLine(textarea);
    var newText = "";
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = textarea.value.substring(start, end);
    selectedText.split('\n').forEach((line) => {
      var orderedList = "";
      var lineType = getLineType(selectedText);
      switch(lineType[0]) {
        case TYPE_OL:
          orderedList = line.replace(lineType[1][0], "");
          break;
        case TYPE_NONE:
          orderedList = "1. " + line;
          break;
        default:
          orderedList = line.replace(lineType[1][0], "1. ");
      }
      newText = newText + orderedList + "\n";
    });
    textarea.value = textarea.value.substring(0, start) + newText.trimEnd() + textarea.value.substring(end);
    textarea.setSelectionRange(start, start + newText.trimEnd().length);
    textarea.focus();
  };

  /**
   * Make the textarea selection a task list element
   * 
   * @param {Element} textarea 
   */
  window.setTaskList = function (textarea) {
    extendSelectionToEntireLine(textarea);
    var newText = "";
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = textarea.value.substring(start, end);
    selectedText.split('\n').forEach((line) => {
      var taskList = "";
      var lineType = getLineType(line);
      switch(lineType[0]) {
        case TYPE_DONE_TASK:
          taskList = line.replace("[x]", "[ ]");
          break;
        case TYPE_TASK:
          taskList = line.replace("[ ]", "[x]");
          break;
        case TYPE_NONE:
          taskList = "- [ ] " + line;
          break;
        default:
            taskList = line.replace(lineType[1][0], "- [ ] ");
      }
      newText = newText + taskList + "\n";
    });
    textarea.value = textarea.value.substring(0, start) + newText.trimEnd() + textarea.value.substring(end);
    textarea.setSelectionRange(start, start + newText.trimEnd().length);
    textarea.focus();
  };
  /**
   * Make the textarea selection a unordered list element
   * 
   * @param {Element} textarea 
   */
  window.setCodeBlock = function (textarea) {
    extendSelectionToEntireLine(textarea);
    var newText = "";
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = textarea.value.substring(start, end);
    textarea.value = textarea.value.substring(0, start) + "```\n" + selectedText + "\n```\n" + textarea.value.substring(end);
    textarea.setSelectionRange(start + 3, start + 3);
    textarea.focus();
  };
})(window);