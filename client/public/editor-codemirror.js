
function cmImport() {
  return window['editor_codemirror'];
}

function completionChangePlugin(callback) {
  const { ViewPlugin, selectedCompletion } = cmImport();
  return ViewPlugin.fromClass(class {
      prevCompletion = null;

      constructor(view) {
        // this.prevCompletion = selectedCompletion(view.state);
        // callback(this.prevCompletion);
      }

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
      // console.log('toDOMming ', this.checked)
      let wrap = document.createElement("span")
      wrap.setAttribute("aria-hidden", "true")
      // wrap.className = "cm-boolean-toggle"
      wrap.className = (this.params.contentClassNames ?? []).join(' ');
      wrap.innerText = this.params.content;
      return wrap
    }

    ignoreEvent() { return false }
  }
  const pluginDecorationSetWrapper = { decorations: Decoration.set([]) }
  const checkboxPlugin = ViewPlugin.fromClass(class {
    decorations
    constructor() {
      this.decorations = pluginDecorationSetWrapper.decorations;
    }
    update(upd) {
      if (upd.docChanged) {
        pluginDecorationSetWrapper.decorations = pluginDecorationSetWrapper.decorations.map(upd.changes);
      }
      this.decorations = pluginDecorationSetWrapper.decorations;
    }
  }, {
    decorations: v => pluginDecorationSetWrapper.decorations,
  })

  const syntaxColorCache = {};
  const getSyntaxColorMarker = (color) => {
    if (!syntaxColorCache[color]) {
      syntaxColorCache[color] = Decoration.mark({ class: `${color}`});
    }
    return syntaxColorCache[color];
  }
  const setSyntaxHighlight = StateEffect.define({
    map: ({from, to, color}, change) => ({from: change.mapPos(from), to: change.mapPos(to), color})
  })

  const clearSyntaxHighlight = StateEffect.define({
      map: ({}, change) => ({})
  })

  const syntaxHighlightField = StateField.define({
    create() {
      return Decoration.none
    },
    update(underlines, tr) {
      // console.log('mapping underlinges:', underlines, 'w/ tr:', tr);
      underlines =  underlines.map(tr.changes)
      for (let e of tr.effects) {

        if (e.is(setSyntaxHighlight)) {
          // console.log('added hl from', e.value.from,' to', e.value.to, ', doclen:', );
          const doclen = tr.state.doc.length;
          const from = Math.max(0, Math.min(doclen, e.value.from));
          const to = Math.max(from + 1, Math.min(doclen + 1, e.value.to));
          const additions = [
            getSyntaxColorMarker(e.value.color).range(from, to),
          ];
          underlines = underlines.update({
            add: additions,
          })
          }
        if (e.is(clearSyntaxHighlight)) {
            underlines = underlines.update({ filter: () => false });
        }
      }
      return underlines
    },
    provide: f => EditorView.decorations.from(f)
  })

  return { setSyntaxHighlight, clearSyntaxHighlight, syntaxHighlightField, checkboxPlugin, AfterLabelWidget, pluginDecorationSetWrapper };
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
    const { EditorView, basicSetup, Compartment, languages, vscodeDark, vscodeLight, indentWithTab, keymap, StateEffect, linter, Decoration, autocompletion, hoverTooltip, closeHoverTooltips} = cmImport();

    const themeCompartment = new Compartment();
    const langCompartment = new Compartment();

    async function myCompletions(context) {
      const line = editor.state.doc.lineAt(context.pos);
      const column = context.pos - line.from + 1;
      const completeRes = await window.HandleLspLikeInteraction?.('complete', { line: line.number, column, });
      console.log('awaited:', completeRes, 'from', { line, column });
      if (!completeRes) {
        return null;
      }
      console.log('TODO map', completeRes);
      const fromLine = editor.state.doc.line(Math.max(1, Math.min(completeRes.from.line, editor.state.doc.lines)));
      return {
        from: fromLine.from + Math.max(0, Math.min(completeRes.from.column - 1, fromLine.length)),
        options: completeRes.suggestions.map((sug, nodeIndex) => ({
          label: sug.label,
          type: 'text',
          nodeIndex, // <-- used by TextProbeManager to detect index
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

    const { setSyntaxHighlight, clearSyntaxHighlight, syntaxHighlightField, checkboxPlugin, AfterLabelWidget, pluginDecorationSetWrapper } = syntaxHighlightEffect();

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
          checkboxPlugin,
          autocompletion({
            override: [myCompletions]
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
    const doUpdateSpanHighlight = (span, stickies) => {
      const effects = [];

      const createSetter = (span, color) => {
        if (!span.lineStart || !span.lineEnd) {
          return;
        }
        const startLine = editor.state.doc.line(clampLine(span.lineStart));
        const endLine = editor.state.doc.line(clampLine(span.lineEnd));
        const clampPos = pos => Math.max(startLine.from, Math.min(pos, startLine.to));
        effects.push(setSyntaxHighlight.of({ from: clampPos(startLine.from + span.colStart - 1, startLine.to), to: clampPos(endLine.from + span.colEnd), color }));
      }
      if (span && span.lineStart && span.lineEnd) {
        // if (!lastSetSpan || Object.keys(lastSetSpan).every(key => lastSetSpan[key] === span[key])) {
        //   lastSetSpan = span;
          createSetter(span, 'cm-highlight-rag');
        // }
      } else {
        // if (lastSetSpan !== null) {
        //   lastSetSpan = null;
          effects.push(clearSyntaxHighlight.of({}));
        // }
      }
      if (stickies) {
        // stickies.forEach(({ classNames, span,  content, contentClassNames }) => {

        // });
        // pluginDecorationSetWrapper.decorations = () => {
          const widgets = [];
          stickies.forEach(({ classNames, span,  content, contentClassNames }) => {
            if (content) {
              const line = editor.state.doc.line(clampLine(span.lineEnd));
              const loc = Math.max(line.from, Math.min(line.to, line.from + span.colEnd))
              widgets.push(
                Decoration.widget({ widget: new AfterLabelWidget({ content, contentClassNames}), side: 1, }).range(loc, loc)
              );
            } else {
              createSetter(span, classNames.join(' '));
            }
          });
          // return widgets;
          pluginDecorationSetWrapper.decorations = Decoration.set(widgets, /*sort=*/true);;
        // }
        // Decoration.set(widgets, /*sort=*/true);
      }
      if (!editor.state.field(syntaxHighlightField, false)) {
          effects.push(StateEffect.appendConfig.of([syntaxHighlightField]));
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
        const round = pos => {
          // pos.bottom = pos.bottom | 0;
          // pos.top = pos.top | 0;
          // pos.left = pos.left | 0;
          // pos.right = pos.right | 0;
          pos.height = pos.bottom - pos.top;
        }
        round(startPos);
        round(endPos);

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
          // console.log('len:', len, 'from', startPos, endPos)


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
          cv.onpointerenter = () => {
            console.log('enter');
          }

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
      // console.log('refreshing cool markers');
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
        // console.log('adding proble', problemId)
        diagnostics[problemId] = diag;
        return {
          clear: () => {
            // console.log('deleting proble', problemId)
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
