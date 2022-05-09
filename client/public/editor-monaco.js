
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
    const coolMarkerDescriptors = {};
    let activeCoolMarkers = {};
    const refreshCoolMarkers = () => {
      // console.warn('refresh markers!! Len:', Object.values(activeCoolMarkers).length);
      // const pendingRemovalMarkers = new Set(Object.keys(activeCoolMarkers));
      Object.values(activeCoolMarkers).forEach(m => m.forEach(dot => dot.remove()));
      activeCoolMarkers = {};
      // activeCoolMarkers.length = 0;

      const editorRect = inw.getBoundingClientRect();

      Object.entries(coolMarkerDescriptors).forEach(([descriptorId, descriptor]) => {
        const { style: [leftStyle, rightStyle], start, end, message } = descriptor;
        const startLine = start >>> 12;
        const endLine = end >>> 12;
        const startPos = editor.getScrolledVisiblePosition({ lineNumber: (start >>> 12), column: start & 0xFFF });
        const endPos = editor.getScrolledVisiblePosition({ lineNumber: (end >>> 12), column: end & 0xFFF });
        console.log('endPos:', endPos)

        if (startPos.top < 0 && endPos.top < 0) {
          return;
        }

        const dots = [];
        {
          const len = 2 + Math.sqrt((Math.abs(startPos.top - endPos.top) ** 2) + ((Math.abs(startPos.left - endPos.left) ** 2)));
          const height = 6;
          const vpad = 6;
          const cv = document.createElement('canvas');
          cv.width = len;
          cv.height = height + vpad;
          console.log('len:', len, 'from', startPos, endPos)


          let color = '#F00';
          if (/^#[a-fA-F0-9]{4}$/.test(message)) {
            color = message;
          }
          const ctx = cv.getContext('2d');
          ctx.fillStyle = color;
          ctx.strokeStyle = color;

          /**
           *          |\
           *  ________| \
           * |________   |
           *          | /
           *          |/
           */
          const midh = 2;


          // ctx.fillRect(0, 4, len, 4);
          // ctx.lineTo(len - 4, 0);
          ctx.beginPath();       // Start a new path
          ctx.translate(0, vpad / 2);

          const arrowWidth = Math.min(len / 2, 10);
          const tipWidths = [leftStyle, rightStyle].map(style => style === 'p' ? 2 : arrowWidth);
          let tipw = tipWidths[0];
          const barTop = ((height - midh) / 2);
          const barBot = ((height + midh) / 2);

          // const leftStyle = 'a';
          // const rightStyle = 'a';

          // Starts at underside, just to the right of the edge start
          ctx.moveTo(tipw, barBot);
          switch (leftStyle) {
            case 'a': {
              ctx.lineTo(tipw, height);
              ctx.lineTo(0, height / 2);
              ctx.lineTo(tipw, 0);
              ctx.lineTo(tipw, barTop);
              break;
            }
            default:
              console.log('Unknown left line style "', leftStyle, '", rendering plain');
            case 'p': {
              ctx.lineTo(0, barBot);
              ctx.lineTo(0, barTop);
              ctx.lineTo(tipw, barTop);
            }
          }
          const drawCurvedMidsection = startLine === endLine && ((len - (2 * tipw)) > 16);
          const curvedMidsectionLen = Math.max(6, Math.min(10, len / 3));
          const drawCurve = (renderer) => {
            const smoothness = 10;
            for (let i = 0; i < smoothness; ++i) {
              const ang = (Math.PI / 2) * (i / (smoothness - 1));
              renderer(1 - Math.cos(ang), Math.sin(ang));
            }
          };
          if (drawCurvedMidsection) {
            // If 'long enough' (arbitrary threshold) AND a straight line, curve it slightly to avoid covering the text
            ctx.lineTo(tipw + 2, barTop);

            // Straight edge:
            {
              // ctx.lineTo(tipw + 6, -vpad / 2);
              // ctx.lineTo(len - tipw - 6, -vpad / 2);
            }
            // Curved edge
            {
              drawCurve((cos, sin) => {
                const x = tipw + 2 + (curvedMidsectionLen * cos);
                const y = barTop + (-vpad/2 - barTop) * sin;
                ctx.lineTo(x, y);
              });
              tipw = tipWidths[1];
              drawCurve((cos, sin) => {
                const x = len - tipw - 2 - curvedMidsectionLen + (curvedMidsectionLen * sin);
                const y = -vpad/2 + (barTop - (-vpad/2)) * cos;
                ctx.lineTo(x, y);
              });
            }

            // ctx.lineTo(len - tipw - 6, -vpad / 2);
            ctx.lineTo(len - tipw - 2, barTop);
          } else {
            tipw = tipWidths[1];
          }

          ctx.lineTo(len - tipw, barTop);
          switch (rightStyle) {
            case 'a': {
              ctx.lineTo(len - tipw, 0);
              ctx.lineTo(len, height / 2);
              ctx.lineTo(len - tipw, height);
              ctx.lineTo(len - tipw, barBot);
              break;
            }
            default:
              console.log('Unknown left line style "', leftStyle, '", rendering plain');
            case 'p': {
              ctx.lineTo(len, barTop);
              ctx.lineTo(len, barBot);
              ctx.lineTo(len - tipw, barBot);
            }
          }
          if (drawCurvedMidsection) {
            ctx.lineTo(len - tipw - 2, barBot);

            // Straight edge
            // {
            //   ctx.lineTo(len - tipw - 6, barBot - barTop - vpad / 2);
            //   ctx.lineTo(tipw + 6, barBot - barTop - vpad / 2);
            // }
            // Curved edge
            {
              // const barOffset = barBot - barBot;
              drawCurve((cos, sin) => {
                const x = len - tipw - 2 + (-curvedMidsectionLen * cos);
                const y = barTop + (-vpad/2 - barTop) * sin;
                ctx.lineTo(x, midh + y);
              });
              tipw = tipWidths[0];
              drawCurve((cos, sin) => {
                const x = tipw + 2 + curvedMidsectionLen + (-curvedMidsectionLen * sin);
                const y = -vpad/2 + (barTop - (-vpad/2)) * cos;
                ctx.lineTo(x, midh + y);
              });
              // drawCurve((cos, sin) => {
              //   const x = tipw + 2 + (curvedMidsectionLen * cos);
              //   const y = barTop + (-vpad/2 - barTop) * sin;
              //   ctx.lineTo(x, barOffset + y);
              // });
            }
            ctx.lineTo(tipw + 2, barBot);
          } else {
            tipw = tipWidths[0];
          }
          ctx.lineTo(tipw, barBot);
          // ctx.stroke();
          ctx.fill();

          cv.style.userSelect = 'none';
          cv.style.pointerEvents = 'none';
          cv.style.position = 'absolute';
          cv.onmouseenter = () => {
            console.log('enter', color);
          }

          cv.style.top = `${editorRect.top + startPos.top + (startPos.height - cv.height) / 2}px`;
          cv.style.left = `${editorRect.left + startPos.left}px`;
          cv.style.transformOrigin = 'left';

          cv.style.transform = `rotate(${180 * Math.atan2(endPos.top - startPos.top, endPos.left - startPos.left) / Math.PI}deg)`;
          document.body.appendChild(cv);
          dots.push(cv);
        }
        activeCoolMarkers[descriptorId] = dots;
      });
    }
    editor.onDidScrollChange(() => {
      refreshCoolMarkers();
    });
    editor.onDidChangeModelContent((e) => {
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

      });

      Object.values(coolMarkerDescriptors).forEach((descriptor) => {
        const { start, end, message } = descriptor;
        adjusters.forEach(adj => {
          const [ls, cs] = adj(start >>> 12, start & 0xFFF);
          const [le, ce] = adj(end >>> 12, end & 0xFFF);

          descriptor.start = (ls << 12) + cs;
          descriptor.end = (le << 12) + ce;
        });
      });
      refreshCoolMarkers();

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
      run: (ed) => {
        const pos = ed.getPosition();
        window.RagQuery && window.RagQuery(pos.lineNumber, pos.column, true);
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
      markText: ({ severity, lineStart, colStart, lineEnd, colEnd, message }) => {
        const problemId = `problem_${++problemIdGenerator}`;

        if (/^line-[pa]{2}$/.test(severity)) {

          coolMarkerDescriptors[problemId] = { style: [...severity.slice('line-'.length)], start: (lineStart << 12) + colStart, end: (lineEnd << 12) + colEnd, message };
          refreshCoolMarkers();
          return {
            clear: () => {
              delete coolMarkerDescriptors[problemId];
              activeCoolMarkers[problemId]?.forEach(dot => dot.remove());
              delete activeCoolMarkers[problemId];
              // refreshCoolMarkers();
            }
          };
        }
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
              severity: ({
                'info': 2,
                'warning': 4
              })[severity] ?? 8, // default to 'error' (8)
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
