
window.defineEditor(
  'Monaco',
  () => ({
    script: [
      'vs/loader.js',
      'vs/editor/editor.main.js',
    ],
    style: [],
    predicate: () => 'monaco' in window,
  }),
  (value, onChange) => {
    const inw = document.getElementById('input-wrapper');
    inw.classList.add('input-Monaco');
    inw.innerHTML = '';

    const editor = monaco.editor.create(inw, {
      value,
      language: 'java',
      theme: 'dark',
      scrollBeyondLastLine: false,
      automaticLayout: true,
    });
    editor.onDidChangeModelContent((e) => {
      console.log('onDidChangeModelContent:', e);
      const adjusters = [];
      e.changes.forEach((chg) => {
        // Each change is pushing or pulling in some direction
        let { startLineNumber, startColumn, endLineNumber, endColumn } = chg.range;
        let insertLines = 0;
        let insertCols = 0;
        if (chg.text) {
          // Inserted (and possibly replaced) text
          insertLines = chg.text.split('\n').length - 1;
          insertCols = insertLines
            ? (chg.text.slice(chg.text.lastIndexOf('\n')).length)
            : chg.text.length;
        }

        console.log('handle range change', startLineNumber, startColumn, endLineNumber, endColumn);
        console.log('tex:', chg.text);

        let lineDiff = startLineNumber - endLineNumber;
        let colDiff = startColumn - endColumn;
        if ((lineDiff || colDiff) && (insertLines || insertCols)) {
          // Both removal and insertion at the same time
          // Calculate which is "bigger" and keep a subset of the changes



          // lineDiff = startLineNumber - endLineNumber;
          // colDiff = startColumn - endColumn;
        }

        const applyDeletion = (line, col) => {
          if (!lineDiff && !colDiff) {
            return [line, col];
          }

          if (startLineNumber === endLineNumber) {
            // Just a column diff
            if (line === startLineNumber) {
              if (col >= startColumn && col <= endColumn) {
                return [line, startColumn];
              }
              if (col > endColumn) {
                return [line, col + startColumn - endColumn];
              }
            }
            return [line, col];
          } else {
            // Multi-line diff
            if (line === startLineNumber) {
              return [line, Math.min(col, startColumn)];
            }
            if (line === endLineNumber) {
              if (col > endColumn) {
                return [startLineNumber, col + startColumn - endColumn];
              }
              return [startLineNumber, startColumn];
            }
            if (line > startLineNumber && line < endLineNumber) {
              // In between
              return [startLineNumber, startColumn];
            }
            if (line > endLineNumber) {
              return [line + startLineNumber - endLineNumber, col];
            }
            return [line, col];
          }
        };

        const applyInsertion = (line, col) => {
          if (!chg.text) {
            return [line, col];
          }

          // Inserted (and possibly replaced) text
          const insertionEndLine = startLineNumber + chg.text.split('\n').length - 1;
          const insertionEndColumn = chg.text.includes('\n')
            ? (chg.text.slice(chg.text.lastIndexOf('\n')).length)
            : (startColumn + chg.text.length);

          if (line < startLineNumber || (line === startLineNumber && col < startColumn)) {
            // Before insertion, unaffected
            return [line, col];
          }
          const sumLine = line + insertionEndLine - startLineNumber;
          if (line > startLineNumber) {
            // On some line after insertion, just shift down
            return [sumLine, col];
          }
          // else, on the same line as insertion, after start column
          const sumCol = col + insertionEndColumn - startColumn;
          return [sumLine, sumCol];
        };

        const isWithinStartEnd = (line, col) => {
          const packedStart = (startLineNumber << 12) + startColumn;
          const packedEnd = (endLineNumber << 12) + endColumn;
          const packedCmp = (line << 12) + col;
          return packedStart <= packedCmp && packedCmp <= packedEnd;
        };

        adjusters.push((line, col) => {
          let [backLine, backCol] = applyDeletion(line, col);
          const didMoveBack = (startLineNumber !== endLineNumber || startColumn !== endColumn) && isWithinStartEnd(line, col);
          let [fwdLine, fwdCol] = applyInsertion(backLine, backCol);
          const didMoveFwd = fwdLine > backLine || fwdCol > backCol;

          console.log('dm:', [didMoveBack, didMoveFwd], 'base:', {line,col}, '; back:',{line:backLine,col:backCol}, '; fwd:', {line:fwdLine,col:fwdCol});
          if (didMoveBack && didMoveFwd) {
            //
            if (fwdLine > line || (fwdLine === line && fwdCol > col)) {
              return [line, col];
            }
          }
          return [fwdLine, fwdCol];


          // Removed some text as part of the edit
          // Pull existing markers "up and to the left" accordingly

          /*
          Assome somebody marks 'bc\nde' in this file and presses backspace:
            abc
            def
          Any marker within bc\nde should have its column set to 1 (=startColumn)
          Any marker before 1 should be unchanged
          // Any marker after endColumn on endLine should be adjusted by startColumn-endColumn
          */

        });

        // if (lineDiff || colDiff) {
        //   // Removed some text as part of the edit
        //   // Pull existing markers "up and to the left" accordingly

        //   /*
        //   Assome somebody marks 'bc\nde' in this file and presses backspace:
        //     abc
        //     def
        //   Any marker within bc\nde should have its column set to 1 (=startColumn)
        //   Any marker before 1 should be unchanged
        //   // Any marker after endColumn on endLine should be adjusted by startColumn-endColumn
        //   */
        //   if (startLineNumber === endLineNumber) {
        //     // Just a column diff
        //     adjusters.push((line, col) => {
        //       if (line === startLineNumber) {
        //         if (col >= startColumn && col <= endColumn) {
        //           return [line, startColumn];
        //         }
        //         if (col > endColumn) {
        //           return [line, col + startColumn - endColumn];
        //         }
        //       }
        //       return [line, col];
        //     });
        //   } else {
        //     // Multi-line diff
        //     adjusters.push((line, col) => {
        //       if (line === startLineNumber) {
        //         return [line, Math.min(col, startColumn)];
        //       }
        //       if (line === endLineNumber) {
        //         if (col > endColumn) {
        //           return [startLineNumber, col + startColumn - endColumn];
        //         }
        //         return [startLineNumber, startColumn];
        //       }
        //       if (line > startLineNumber && line < endLineNumber) {
        //         // In between
        //         return [startLineNumber, startColumn];
        //       }
        //       if (line > endLineNumber) {
        //         return [line + startLineNumber - endLineNumber, col];
        //       }
        //       return [line, col];
        //     });
        //   }
        //   return;
        // }
        // if (chg.text) {
        //   // Inserted (and possibly replaced) text
        //   endLineNumber = startLineNumber + chg.text.split('\n').length - 1;
        //   endColumn = chg.text.includes('\n')
        //     ? (chg.text.slice(chg.text.lastIndexOf('\n')).length)
        //     : (startColumn + chg.text.length);

        //   adjusters.push((line, col) => {
        //     if (line < startLineNumber || (line === startLineNumber && col < startColumn)) {
        //       // Before insertion, unaffected
        //       return [line, col];
        //     }
        //     const sumLine = line + endLineNumber - startLineNumber;
        //     if (line > startLineNumber) {
        //       // On some line after insertion, just shift down
        //       return [sumLine, col];
        //     }
        //     // else, on the same line as insertion, after start column
        //     const sumCol = col + endColumn - startColumn;
        //     return [sumLine, sumCol];

        //     // if (line > endLineNumber) {
        //     //   return [sumLine, col];
        //     // }
        //     // if (startLineNumber === endLineNumber) {
        //     //   if (col < startColumn) {
        //     //     return [line, col];
        //     //   }
        //     //   // else, same line and after start of insertion
        //     //   return [line, sumCol];
        //     // }
        //     // // else, multi-line insertion
        //     // if (line === startLineNumber) {
        //     //   if (col < startColumn) {
        //     //     return [line, col];
        //     //   }
        //     //   return [sumLine, sumCol];
        //     // }
        //     // if (line === endLineNumber) {
        //     //   return [sumLine, ]
        //     // }
        //     // // Else, somewhere in middle of multi-line insertion
        //   });
        // }
      });
      onChange(editor.getValue(), adjusters);
    });
    editor.addAction({
      id: 'pasta-reg-query',
      label: 'RAG Query',
      precondition: null,

      contextMenuGroupId: 'navigation',
      contextMenuOrder: 1,
      run: (ed) => {
        const pos = ed.getPosition();
        window.RagQuery && window.RagQuery(pos.lineNumber, pos.column);
        // console.log('ed @ ', ed, '||', ed.getPosition());
      },
    });
    editor.addAction({
      id: 'pasta-shortcut-query',
      label: '',
      precondition: null,

      // contextMenuGroupId: 'navigation',
      // contextMenuOrder: 2,
      run: (ed) => {
        const pos = ed.getPosition();
        window.RagQuery && window.RagQuery(pos.lineNumber, pos.column, 'Program');
        // console.log('ed @ ', ed, '||', ed.getPosition());
      },
      keybindings: [
        monaco.KeyMod.chord(
          monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyK,
          monaco.KeyCode.KeyP
        ),
      ],
    });

    monaco.languages.registerCompletionItemProvider('cpp', {
      triggerCharacters: ['.', '('],
      provideCompletionItems: async (model, position, token) => {
        if (!window.DoAutoComplete) { return null; }
        let result;
        try {
          result = await DoAutoComplete(position.lineNumber, position.column, editor.getValue());
        } catch (e) {
          console.log('AutoComplete failed:', e);
          return null;
        }
        const mapKind = (kind) => ({
          var: monaco.languages.CompletionItemKind.Variable,
          func: monaco.languages.CompletionItemKind.Function,
          member: monaco.languages.CompletionItemKind.Field,
        })[kind] || monaco.languages.CompletionItemKind.Text;
        // console.log('provcompl', position);
        return {
          suggestions: result.map(({ kind, insert, info }) => ({
            label: insert,
            kind: mapKind(kind),
            detail: info,
            insertText: insert,
          }))
        };

        // [{
        //   label: 'label here',
        //   kind: monaco.languages.CompletionItemKind.Class,
        //   detail: 'details here',
        //   insertText: 'insert text'
        // }],
      }
    });

    let lastDecorations = [];
    const updateSpanHighlight = (span) => {
      let newDecorations = [];
      if (span) {
        newDecorations = [
          {
            range: new monaco.Range(span.lineStart, span.colStart, span.lineEnd, span.colEnd + 1),
            options: { inlineClassName: 'monaco-rag-highlight' }
          }
        ];
      }
      lastDecorations = editor.deltaDecorations(lastDecorations, newDecorations);
    };

    const registerStickyMarker = (span) => {
      let decoIds = editor.deltaDecorations([], [{
        range: new monaco.Range(span.lineStart, span.colStart, span.lineEnd, span.colEnd + 1),
        options: { inlineClassName: 'monaco-rag-highlight' },
      }]);
      // if (span) {
      //   decoIds = [
      //     {
      //       range: new monaco.Range(span.lineStart, span.colStart, span.lineEnd, span.colEnd + 1),
      //       options: { inlineClassName: 'monaco-rag-highlight' }
      //     }
      //   ];
      // }
      // lastDecorations = editor.deltaDecorations(lastDecorations, decoIds);
      let lastKnownPos = {...span};
      return ({
        // TODO update function so that probeModal can move around if needed
        getSpan: () => {
          const range = editor.getModel().getDecorationRange(decoIds[0]);
          console.log('got deco range:', range);
          if (range) {
            lastKnownPos.lineStart = range.startLineNumber;
            lastKnownPos.colStart = range.startColumn;
            lastKnownPos.lineEnd = range.endLineNumber;
            lastKnownPos.colEnd = range.endColumn;
          }
          return lastKnownPos;
        },
        remove: () => {
          editor.deltaDecorations(decoIds, []);
        }
      })
    };
    // window.onresize = () => {
    //   console.log('Window resize');
    //   editor.layout({});
    // };

    // let numActiveMarkers = 0;
    let problemIdGenerator = 0;
    return {
      setLocalState: (value) => editor.setValue(value),
      getLocalState: () => editor.getValue(),
      updateSpanHighlight,
      registerStickyMarker,
      markText: ({ lineStart, colStart, lineEnd, colEnd, message }) => {
        const problemId = `problem_${++problemIdGenerator}`;
        // ++numActiveMarkers;
        monaco.editor.setModelMarkers(
          editor.getModel(),
          problemId,
          [
            {
              startLineNumber: lineStart,
              startColumn: colStart,
              endLineNumber: lineEnd,
              endColumn: colEnd + 1,
              message,
              severity: 8,
            }
          ]
        );
        return {
          clear: () => {
            // --numActiveMarkers;
            monaco.editor.setModelMarkers(editor.getModel(), problemId, [])
            // console.log('cleared, numActive:', numActiveMarkers);
          },
        }
      },
      themeToggler: light => monaco.editor.setTheme(light ? 'vs-light' : 'vs-dark'),
    };
  },
);
