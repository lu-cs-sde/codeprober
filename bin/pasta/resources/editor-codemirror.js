
window.defineEditor(
  'CodeMirror',
  () => ({
    script: [
      'cm/codemirror.js',
      'cm/clike.js',
      'cm/show-hint.js',
    ],
    style: [
      'cm/codemirror.css',
      'cm/monokai.css',
    ],
    predicate: () => 'CodeMirror' in window,
  }),
  (value, onChange) => {
    console.log('init w/ value len:', value.length);
    const inw = document.getElementById('input-wrapper');
    inw.innerHTML = '';
    const input = window.CodeMirror(inw, {
      mode: 'text/x-csrc',
      value: value,
      lineNumbers: true,
      extraKeys: {"Ctrl-Space": "autocomplete"},
      hintOptions: {hint: synonyms},
    });
    input.setSize(null, '100%');

    input.on('change', (cm) => onChange(cm.getValue()));

    async function synonyms(cm, option) {
      if (!window.DoAutoComplete) { return null; }

      const cursor = cm.getCursor();
      console.log('curses', cursor);
      const line = cm.getLine(cursor.line)
      // console.log('l', line);
      // const column = cm.getColumn(cursor.column)
      let start = cursor.ch, end = cursor.ch
      while (start && /\w/.test(line.charAt(start - 1))) --start
      while (end < line.length && /\w/.test(line.charAt(end))) ++end

      let result;
      try {
        result = await window.DoAutoComplete(cursor.line + 1, cursor.ch + 1, cm.getValue());
      } catch (e) {
        console.warn('AutoComplete failed', e);
        return null;
      }

      return {
        list: result.map(({ insert, info }) => ({
          text: insert,
          displayText: info,
        })),
        from: CodeMirror.Pos(cursor.line, start),
        to: CodeMirror.Pos(cursor.line, end),
      };
    }

    return {
      setLocalState: (value) => input.setValue(value),
      getLocalState: () => input.getValue(),
      markText: ({ lineStart, colStart, lineEnd, colEnd }) => input.markText(
        { line: lineStart - 1, ch: colStart - 1 },
        { line: lineEnd - 1, ch: colEnd },
        { className: 'feedback-highlight' },
      ),
      themeToggler: light => input.setOption('theme', light ? 'default' : 'monokai'),
    };
  },
);
