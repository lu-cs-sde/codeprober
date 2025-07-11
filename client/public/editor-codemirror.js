
function cmImport() {
  return window['editor_codemirror'];
}

function completionChangePlugin(callback) {
  const { ViewPlugin, selectedCompletion } = cmImport();
  return ViewPlugin.fromClass(class {
      prevCompletion = null;
      constructor(view) { }

      update(update) {
        if (update.docChanged || update.selectionSet || update.transactions.length) {
          const current = selectedCompletion(update.state);
          if (current !== this.prevCompletion) {
            this.prevCompletion = current;
            callback(current);
          }
        }
      }
    });
}

function syntaxHighlightEffect() {
  const { Decoration, StateEffect, StateField, EditorView, WidgetType, ViewPlugin } = cmImport();

  class AfterLabelWidget extends WidgetType {
    constructor(params) {
      super()
      this.params = params;
    }

    eq(other) { return other.checked == this.checked }

    toDOM() {
      let wrap = document.createElement("span")
      wrap.setAttribute("aria-hidden", "true")
      wrap.className = (this.params.contentClassNames ?? []).join(' ');
      wrap.innerText = this.params.content;
      return wrap
    }

    ignoreEvent() { return false }
  }

  const setAfterLabels = StateEffect.define({
    map: (labels, change) => labels.map(lbl => ({...lbl, loc: change.mapPos(lbl.loc)}))
  })
  const afterLabelField = StateField.define({
    create() {
      return Decoration.none
    },
    update(underlines, tr) {
      underlines =  underlines.map(tr.changes)
      for (let e of tr.effects) {
        if (e.is(setAfterLabels)) {
          underlines = underlines.update({
            filter: () => false,
            add: e.value
              .sort((a, b) => a.pos - b.pos)
              .map(lblCfg => Decoration
                  .widget({ widget: new AfterLabelWidget(lblCfg), side: 1, })
                  .range(lblCfg.pos, lblCfg.pos)
              ),
          });
        }
      }
      return underlines
    },
    provide: f => EditorView.decorations.from(f)
  })

  const classColorCache = {};
  const getClassColorMarker = (clazz) => {
    if (!classColorCache[clazz]) {
      classColorCache[clazz] = Decoration.mark({ class: `${clazz}`});
    }
    return classColorCache[clazz];
  }
  const addClassMark = StateEffect.define({
    map: ({from, to, clazz}, change) => ({from: change.mapPos(from), to: change.mapPos(to), clazz}),
  })
  const clearClassMarks = StateEffect.define({
      map: ({}, change) => ({})
  })

  const classMarkField = StateField.define({
    create() {
      return Decoration.none
    },
    update(underlines, tr) {
      underlines =  underlines.map(tr.changes)
      for (let e of tr.effects) {
        if (e.is(addClassMark)) {
          const doclen = tr.state.doc.length;
          const from = Math.max(0, Math.min(doclen, e.value.from));
          const to = Math.max(from + 1, Math.min(doclen + 1, e.value.to));
          const additions = [
            getClassColorMarker(e.value.clazz).range(from, to),
          ];
          underlines = underlines.update({
            add: additions,
          })
        }
        if (e.is(clearClassMarks)) {
            underlines = underlines.update({ filter: () => false });
        }
      }
      return underlines
    },
    provide: f => EditorView.decorations.from(f)
  })

  return {
    addClassMark, clearClassMarks, classMarkField,
    setAfterLabels, afterLabelField,
  };
}

window.defineEditor(
  'CodeMirror',
  () => ({
    script: [
      'codemirror-bundle.js',
    ],
    style: [],
  predicate: () => 'editor_codemirror' in window,
  }),
  (value, onChange, initialSyntaxHighlight) => {
    const { EditorView, basicSetup, Compartment, languages, vscodeDark, vscodeLight, indentWithTab, keymap, StateEffect, linter, autocompletion, hoverTooltip, closeHoverTooltips, completionStatus, selectedCompletion } = cmImport();

    const themeCompartment = new Compartment();
    const langCompartment = new Compartment();

    async function myCompletions(context) {
      const line = editor.state.doc.lineAt(context.pos);
      const column = context.pos - line.from + 1;
      const completeRes = await window.HandleLspLikeInteraction?.('complete', { line: line.number, column, });
      if (!completeRes) {
        return null;
      }
      const fromLine = editor.state.doc.line(Math.max(1, Math.min(completeRes.from.line, editor.state.doc.lines)));
      setTimeout(() => {
        if (completionStatus(editor.state) === null) {
          // The popup never showed, because no item can be inserted at this point
          // Manually trigger "onClosed"
          if (typeof OnCompletionItemListClosed === 'function') {
            window.OnCompletionItemListClosed();
          }
        }
      })
      return {
        from: fromLine.from + Math.max(0, Math.min(completeRes.from.column - 1, fromLine.length)),
        options: completeRes.suggestions.map((sug, nodeIndex) => ({
          label: sug.label,
          type: 'text',
          nodeIndex, // <-- used by TextProbeManager to detect index
          sort: nodeIndex,
        })),
      };
    }

    const selectSyntaxHighlightExtension = (langId) => {
      const specific = languages[langId.toLowerCase()];
      if (specific && typeof specific === 'function') {
        return specific();
      }
      console.warn('Language', langId, 'not supported, defaulting to java')
      return languages.java();
    }

    const inw = document.getElementById('input-wrapper');
    inw.classList.add('input-CodeMirror');
    inw.innerHTML = '';

    let diagnostics = {};
    const lintExtension = linter(() => {
      const diagList = Object.values(diagnostics);
      diagList.forEach(diag => {
        diag.from = Math.max(0, Math.min(diag.from, editor.state.doc.length));
        diag.to = Math.max(0, Math.min(diag.to, editor.state.doc.length));
      })
      return diagList;
    });

    const { addClassMark, clearClassMarks, classMarkField, afterLabelField, setAfterLabels } = syntaxHighlightEffect();

    const completionChangeListener = completionChangePlugin(compl => {
      setTimeout(() => {
        if (compl) {
          if (typeof window.OnCompletionItemFocused === 'function') {
            window.OnCompletionItemFocused(compl);
          }
        } else {
          if (typeof OnCompletionItemListClosed === 'function') {
            window.OnCompletionItemListClosed();
          }
        }
      })
    });

    // Arbitrary number.
    // Monaco manages this number for us, here we must assign something custom
    // We don't really care what value this has. It just needs to exist on window
    window.CPR_CMD_OPEN_TEXTPROBE_ID = 123;
    const hoverExt = hoverTooltip(async (view, pos) => {
      const line = view.state.doc.lineAt(pos);
      const column = pos - line.from + 1;
      const hoverRes = await window.HandleLspLikeInteraction?.(
        'hover',
        { line: line.number, column, }
      );
      if (!hoverRes) {
        return null;
      }

      const from = lineColToOffset(hoverRes.range.startLineNumber, hoverRes.range.startColumn)
      const to = lineColToOffset(hoverRes.range.endLineNumber, hoverRes.range.endColumn)
      return {
        pos: from,
        end: to,
        create: (view) => {
          const dom = document.createElement('div');
          dom.classList.add('codemirror_context_menu', 'show');
          dom.style.position = 'relative';
          dom.style.padding = '0.25rem';

          hoverRes.contents.forEach(rowCfg => {
            const row = dom.appendChild(document.createElement('div'));
            const reg = new RegExp(`\\\[(.*)\\\]\\\(command:${CPR_CMD_OPEN_TEXTPROBE_ID}\\\)`);
            const matcher = rowCfg.value.match(reg);
            if (matcher) {
              const anchorlike = row.appendChild(document.createElement('span'));
              anchorlike.classList.add('codemirror_action_link');
              anchorlike.innerText = matcher[1];
              row.onclick = () => {
                window.CPR_CMD_OPEN_TEXTPROBE_CALLBACK?.();
                editor.dispatch({ effects: [closeHoverTooltips] });
              };
            } else {
              row.innerText = rowCfg.value;
            }
          })

          return { dom, };
        }
      }
    });

    let editor = new EditorView({
      extensions: [
          basicSetup,
          keymap.of([
            indentWithTab,
            {
              key: 'Mod-k p',
              run: () => performRagQuery(true),
            }
          ]),
          themeCompartment.of(vscodeDark),
          langCompartment.of(selectSyntaxHighlightExtension(initialSyntaxHighlight)),
          lintExtension,
          autocompletion({
            override: [myCompletions],
            compareCompletions: (a, b) => (b.sort ?? 0) - (a.sort ?? 0),
          }),
          completionChangeListener,
          hoverExt,
      ],
      parent: inw,
      doc: value,
    });
    const lineColToOffset = (line, col) => {
      const l = editor.state.doc.line(clampLine(line));
      return Math.max(l.from, Math.min(l.to, l.from + col - 1));
    }

    let updateSpanHighlightTimeout = -1;
    const updateSpanHighlight = (span, stickies) => {
      // Monaco seems to handle updating "span highlights" very rapidly
      // Maybe it does internal debouncing?
      // Anyway, very rapid updates to Codemirror can cause an unnecessarily large amount of state changes, and even has caused problems with the hoverTooltip plugin being called unnecessarily often.
      // This is "fixed" by adding a very short debounce timer.
      clearTimeout(updateSpanHighlightTimeout);
      updateSpanHighlightTimeout = setTimeout(() => doUpdateSpanHighlight(span, stickies), 10);
    }
    const clampLine = (line, state = editor.state) => Math.max(1, Math.min(line, state.doc.lines))
    let hadStickiesRecently = false;
    const doUpdateSpanHighlight = (span, stickies) => {
      const effects = [];

      const createSetter = (span, clazz) => {
        if (!span.lineStart || !span.lineEnd) {
          return;
        }
        const startLine = editor.state.doc.line(clampLine(span.lineStart));
        const endLine = editor.state.doc.line(clampLine(span.lineEnd));
        const clampPos = (pos, line) => Math.max(line.from, Math.min(pos, line.to));
        effects.push(addClassMark.of({
          from: clampPos(startLine.from + span.colStart - 1, startLine),
          to: clampPos(endLine.from + span.colEnd, endLine),
          clazz
        }));
      }
      if (span && span.lineStart && span.lineEnd) {
        createSetter(span, 'cm-highlight-rag');
      } else {
        effects.push(clearClassMarks.of({}));
      }

      let addedAnyAfterLabels = false;
      if (stickies?.length) {
        const afterLabels = [];
        stickies.forEach(lblCfg => {
          if (lblCfg.content) {
            const line = editor.state.doc.line(clampLine(lblCfg.span.lineEnd));
            const pos = Math.max(line.from, Math.min(line.to, line.from + lblCfg.span.colEnd))
            afterLabels.push({ ...lblCfg, pos });
          } else {
            createSetter(lblCfg.span, lblCfg.classNames.join(' '));
          }
        });
        if (afterLabels.length) {
          addedAnyAfterLabels = true;
          effects.push(setAfterLabels.of(afterLabels));
          if (!editor.state.field(afterLabelField, false)) {
              effects.push(StateEffect.appendConfig.of([afterLabelField]));
          }
        }
      }
      if (addedAnyAfterLabels && !hadStickiesRecently) {
        hadStickiesRecently = false;
        effects.push(setAfterLabels.of([]))
      }
      if (!editor.state.field(classMarkField, false)) {
          effects.push(StateEffect.appendConfig.of([classMarkField]));
      }
      editor.dispatch({effects})
    };

    const coolMarkerDescriptors = {};
    let activeCoolMarkers = {};
    const refreshCoolMarkers = () => {
      Object.values(activeCoolMarkers).forEach(m => m.forEach(dot => dot.remove()));
      activeCoolMarkers = {};

      Object.entries(coolMarkerDescriptors).forEach(([descriptorId, descriptor]) => {
        const { style: [leftStyle, rightStyle], start, end, message } = descriptor;
        const startLine = clampLine(start >>> 12);
        const endLine = clampLine(end >>> 12);
        const startPos = editor.coordsAtPos(editor.state.doc.line(startLine).from + (start & 0xFFF) - 1)
        if (!startPos) {
          return;
        }
        const endPos = editor.coordsAtPos(editor.state.doc.line(endLine).from + (end & 0xFFF) - 1)
        if (!endPos) {
          return;
        }
        const calcHeight = pos => {
          pos.height = pos.bottom - pos.top;
        }
        calcHeight(startPos);
        calcHeight(endPos);

        if (startPos.top < 0 && endPos.top < 0) {
          return;
        }

        const dots = [];
        {
          const scale = 5;
          const len = scale * (2 + Math.sqrt((Math.abs(startPos.top - endPos.top) ** 2) + ((Math.abs(startPos.left - endPos.left) ** 2))));
          const height = scale * 6;
          const vpad = scale * 6;
          const cv = document.createElement('canvas');
          cv.width = len;
          cv.height = height + vpad;
          let colorBase = '#F00';
          if (/^#[a-fA-F0-9]{4}$/.test(message)) {
            colorBase = message;
          }
          for (let iteration = 0; iteration < 2; ++iteration) {
            const color = iteration === 0 ? colorBase : (
                `${colorBase.slice(0, 4)}F`
            );

            const ctx = cv.getContext('2d');
            ctx.fillStyle = color;
            ctx.strokeStyle = color;
            ctx.lineWidth = scale/2;

            /**
             *          |\
             *  ________| \
             * |________   |
             *          | /
             *          |/
             */
            const midh = scale * 2;


            // ctx.fillRect(0, 4, len, 4);
            // ctx.lineTo(len - 4, 0);
            ctx.beginPath();       // Start a new path
            ctx.translate(0, vpad / 2);

            const arrowWidth = Math.min(len / 2, scale * 10);
            const tipWidths = [leftStyle, rightStyle].map(style => style === 'p' ? 2 : arrowWidth);
            let tipw = tipWidths[0];
            const barTop = ((height - midh) / 2);
            const barBot = ((height + midh) / 2);

            // const leftStyle = 'a';
            // const rightStyle = 'a';

            // Starts at underside, just to the right of the edge start
            ctx.moveTo(tipw, barBot);
            switch (leftStyle) {
              case 'a':
              case 'A': {
                ctx.lineTo(tipw, height);
                ctx.lineTo(0, height / 2);
                ctx.lineTo(tipw, 0);
                ctx.lineTo(tipw, barTop);
                break;
              }
              default:
                console.log('Unknown left line style "', leftStyle, '", rendering plain');
              case 'p':
              case 'P': {
                ctx.lineTo(0, barBot);
                ctx.lineTo(0, barTop);
                ctx.lineTo(tipw, barTop);
              }
            }
            const drawCurvedMidsection = startLine === endLine && ((len - (2 * tipw)) > (scale*16));
            const curvedMidsectionLen = Math.max(scale * 6, Math.min(scale * 10, len / 3));
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
              case 'a':
              case 'A': {
                ctx.lineTo(len - tipw, 0);
                ctx.lineTo(len, height / 2);
                ctx.lineTo(len - tipw, height);
                ctx.lineTo(len - tipw, barBot);
                break;
              }
              default:
                console.log('Unknown right line style "', leftStyle, '", rendering plain');
              case 'p':
              case 'P': {
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
              }
              ctx.lineTo(tipw + 2, barBot);
            } else {
              tipw = tipWidths[0];
            }
            ctx.lineTo(tipw, barBot);
            ctx.translate(0, -vpad / 2);
            if (iteration == 0) {
              ctx.fill();
            } else {
              ctx.stroke();
            }
            ctx.closePath();
          }

          cv.style.userSelect = 'none';
          cv.style.pointerEvents = 'none';
          cv.style.position = 'absolute';

          cv.style.top = `${startPos.top + (startPos.height - cv.height) / 2}px`;
          cv.style.left = `${startPos.left}px`;
          cv.style.transformOrigin = 'left';
          cv.style.transform = `rotate(${180 * Math.atan2(endPos.top - startPos.top, endPos.left - startPos.left) / Math.PI}deg) scale(${Number(1 / scale).toFixed(2)})`;

          document.body.appendChild(cv);
          dots.push(cv);
        }
        activeCoolMarkers[descriptorId] = dots;
      });
    }

    const performRagQuery = (autoOpenRoot = false) => {
      const docPos = editor.state.selection.main.head;
      const line = editor.state.doc.lineAt(docPos);
      const col = docPos - line.from + 1;
      window.RagQuery && window.RagQuery(line.number, col, autoOpenRoot);
    }
    const contextMenu = document.createElement('div');
    contextMenu.classList.add('codemirror_context_menu');
    document.body.appendChild(contextMenu);
    contextMenu.addEventListener('click', () => {
      contextMenu.classList.remove('show');
    })
    contextMenu.addEventListener('blur', () => {
      contextMenu.classList.remove('show');
    })
    document.addEventListener('mousedown', (e) => {
      if (contextMenu.contains(e.target)) {
        // Inside the menu
        return;
      }
      // Clicking somewhere else, hide the menu
      contextMenu.classList.remove('show');
    })

    editor.dom.addEventListener('contextmenu', (e) => {
      while (contextMenu.lastChild) {
        contextMenu.lastChild.remove();
      }
      const rows = [
        { lbl: 'Create Probe',
          click: performRagQuery,
        }
      ];
      rows.forEach(cfg => {
        const row = document.createElement('div');
        row.classList.add('row');
        const lbl = document.createElement('span');
        lbl.innerText = cfg.lbl;
        row.appendChild(lbl);
        row.onclick = cfg.click;
        contextMenu.appendChild(row);
      })

      contextMenu.style.top = `${e.pageY + 2}px`;
      contextMenu.style.left = `${e.pageX + 2}px`;
      contextMenu.classList.add('show');
      setTimeout(() => contextMenu.focus(), 100);

      e.preventDefault();
      return false;
    });
    editor.dom.querySelector('.cm-scroller').addEventListener('scroll', (e) => {
      refreshCoolMarkers();
    });
    editor.dispatch({ effects: [StateEffect.appendConfig.of(EditorView.updateListener.of(update => {
      if (update.docChanged) {
        onChange(editor.state.doc.toString(), [(line, col) => {
          if (!line || !col) {
            // Invalid pos, noop
            return [line, col];
          }
          const cmlinePre = update.startState.doc.line(clampLine(line, update.startState));
          const posPre = Math.min(cmlinePre.from + col - 1, cmlinePre.to);
          const mappedPos = update.changes.mapPos(posPre, 1); // assoc=1, makes copy-pasting multi-line blocks of text work
          const cmlinePos = editor.state.doc.lineAt(mappedPos);
          return [cmlinePos.number, mappedPos - cmlinePos.from + 1];
        }]);
      }
    }))]});

    let forceUpdateDiagnosticsTimeout = -1;

    let problemIdGenerator = 0;
    return {
      setLocalState: (value) => editor.dispatch({
        changes: {from: 0, to: editor.state.doc.length, insert: value}
      }),
      getLocalState: () => editor.state.doc.toString(), // editor.getValue(),
      updateSpanHighlight,
      markText: ({ severity, lineStart, colStart, lineEnd, colEnd, source, message }) => {
        const problemId = `problem_${++problemIdGenerator}`;

        if (/^LINE_[PA]{2}$/i.test(severity)) {

          coolMarkerDescriptors[problemId] = { style: [...severity.slice('LINE_'.length)], start: (lineStart << 12) + colStart, end: (lineEnd << 12) + colEnd, message };
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

        const diag = {
          from: lineColToOffset(lineStart, colStart),
          to: lineColToOffset(lineEnd, colEnd + 1),
          severity: ({
            'hint': 'hint',
            'info': 'info',
            'warning': 'warning',
          })[severity] ?? 'error', // default to 'error',
          source,
          message: message,
        };
        diagnostics[problemId] = diag;
        return {
          clear: () => {
            delete diagnostics[problemId];
            // Force lint updates: https://discuss.codemirror.net/t/can-we-manually-force-linting-even-if-the-document-hasnt-changed/3570/17
            clearTimeout(forceUpdateDiagnosticsTimeout);
            forceUpdateDiagnosticsTimeout = setTimeout(() => {
              const p = editor.plugin(lintExtension[1]);
              if (p) {
                p.set = true;
                p.force();
              }
            }, 50);
          },
        };
      },
      themeToggler: light => {
        // monaco.editor.setTheme(light ? 'vs-light' : 'vs-dark')
        editor.dispatch({
          effects: themeCompartment.reconfigure(light ? vscodeLight : vscodeDark)
        });

      },
      syntaxHighlightingToggler: langId => {
        editor.dispatch({
          effects: langCompartment.reconfigure(selectSyntaxHighlightExtension(langId)),
        });
      },
    };
  },
);
