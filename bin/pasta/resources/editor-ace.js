
window.defineEditor(
  'Ace',
  () => ({
    script: [
      'ace/ace.js',
      'ace/ext-language_tools.js'
    ],
    style: [],
    predicate: () => 'ace' in window,
  }),
  (value, onChange) => {
    // ace.require
    const inw = document.getElementById('input-wrapper');
    inw.classList.add('input-Ace');
    inw.innerHTML = '';
    input = ace.edit('input-wrapper');
    input.setValue(value);
    input.clearSelection();
    input.setTheme("ace/theme/chrome");
    input.session.setMode("ace/mode/c_cpp");

    input.setOptions({
      enableBasicAutocompletion: [{
        getCompletions: (editor, session, pos, prefix, callback) => {
          if (!window.DoAutoComplete) {
            callback(null, []);
            return;
          }
          console.log('pos:', pos);
          DoAutoComplete(pos.row + 1, pos.column + 1, input.getValue())
            .then((res) => {
              callback(null, res.map(({ kind, insert, info }) => ({
                value: insert,
                meta: info
              })));
            })
            .catch((err) => {
              console.log('AutoComplete failed', err);
              callback(null, []);
            })


          // console.log('enableAuto')
          // // note, won't fire if caret is at a word that does not have these letters
          // callback(null, [
          //   {value: 'hello', score: 1, meta: 'some comment'},
          //   {value: 'world', score: 2, meta: 'some other comment'},
          // ]);
        },
      }],
      // to make popup appear automatically, without explicit _ctrl+space_
      enableLiveAutocompletion: true,
    });

    input.on('change', () => onChange(input.getValue()));
    return {
      setLocalState: (value) => {
        const pos = input.getCursorPosition();
        input.setValue(value, value.length);
        input.moveCursorToPosition(pos);
        input.clearSelection();
      },
      getLocalState: () => input.getValue(),
      themeToggler: light => input.setTheme(light ? 'ace/theme/chrome' : 'ace/theme/monokai'),
      markText: ({ lineStart, colStart, lineEnd, colEnd }) => {
        const id = input.session.addMarker(
          new ace.Range(lineStart - 1, colStart - 1, lineEnd - 1, colEnd),
          'feedback-highlight',
          'background',
          'line',
          true
        );
        return { clear: () => input.session.removeMarker(id) };
      },
    };
  },
);
