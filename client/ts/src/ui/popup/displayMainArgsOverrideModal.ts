import settings from "../../settings";
import createModalTitle from "../create/createModalTitle";
import showWindow from "../create/showWindow";

const getArgs = () => {
  const re = settings.getMainArgsOverride();
  if (!re) { return re; }

  return re.map(item => {
    let str = '';
    let surround = false;
    for (let i = 0; i < item.length; ++i) {
      const ch = item[i]
      switch (ch) {
        case '"': {
          surround = true;
          str = `${str}\\"`;
          break;
        }
        case '\\': {
          str = `${str}\\\\`;
          break;
        }
        case ' ': {
          surround = true;
          // Fall through
        }
        default: {
          str = `${str}${ch}`;
          break;
        }
      }
    }
    if (surround) {
      return `"${str}"`;
    }
    return str;
  }).join(' ');
};

const setArgs = (raw: string, onError: (line: number, col: number, msg: string) => void) => {
  let args: string[] = [];
  let buf: string | null = null;
  let parsePos = 0;
  const commit = () => {
    if (buf !== null) {
      args.push(buf);

    }
    buf = null;
  };
  const getLineColFromStartToPos = (pos: Number) => {
    let line = 1;
    let col = 0;
    for (let i = 0; i < pos; ++i) {
      if (raw[i] == '\n') {
        ++line;
        col = 0;
      } else {
        // This is slightly incorrect for multibyte characters, TODO support properly (if worth the effort)
        ++col;
      }
    }
    return { line, col };
  }
  const parseEscaped = () => {
    const next = raw[parsePos++];
    if (next == '\\') {
      buf = `${buf ?? ''}\\`;
    } else if (next == '"') {
      buf = `${buf ?? ''}"`;
    } else {
      const loc = getLineColFromStartToPos(parsePos - 1);
      onError(loc.line, loc.col, `Unexpected escape character, expected '"' or '\\' after this backslash`);
      throw new Error(`Unexpected escape character`);
    }
  }
  const parseQuoted = () => {
    const start = parsePos;
    buf = '';
    while (parsePos < raw.length) {
      const ch = raw[parsePos++];
      switch (ch) {
        case '"': {
          commit();
          return;
        }
        case '\\': {
          parseEscaped();
          break;
        }
        default: {
          buf = `${buf}${ch}`;
          break;
        }
      }
    }
    const loc = getLineColFromStartToPos(start);
    onError(loc.line, loc.col, `Unterminated string`);
    throw new Error('Unterminated string');
  };
  const parseOuter = () => {
    while (parsePos < raw.length) {
      const ch = raw[parsePos++];
      switch (ch) {
        case '"': {
          commit();
          parseQuoted();
          break;
        }
        case '\\': {
          parseEscaped();
          break;
        }
        case ' ': {
          commit();
          break;
        }
        default: {
          buf = `${buf ?? ''}${ch}`;
          break;
        }
      }
    }
  }

  parseOuter();
  commit();
  // console.log('done parsing @', parsePos)
  settings.setMainArgsOverride(args);
}

const displayMainArgsOverrideModal = (
  setDisableEditButton: (disabled: boolean) => void,
  onChange: () => void,
) => {
  setDisableEditButton(true);
  const windowInstance = showWindow({
    render: (root) => {
      while (root.firstChild) {
        root.firstChild.remove();
      }
      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const headType = document.createElement('span');
          headType.classList.add('syntax-stype');
          headType.innerText = `Main arg override editor`;
          container.appendChild(headType);
        },
        onClose: () => close(),
      }).element);

      const elem = document.createElement('div');
      // elem.style.minHeight = '16rem';
      (elem.style as any) = `
      width: 100%;
      height: 100%;
      `;

      const liveView = document.createElement('div');
      liveView.style.display = 'flex';
      liveView.style.padding = '0.25rem';
      liveView.style.flexDirection = 'row';
      liveView.style.maxWidth = '100%';
      liveView.style.flexWrap = 'wrap';
      // liveView.style.maxHeight = '7.8rem';
      liveView.style.overflow = 'scroll';
      liveView.style.rowGap = '4px';
      const refreshLiveView = () => {
        liveView.innerHTML = '';
        const addTn = (str: string) => {
          const tn = document.createElement('span');
          tn.innerText = str;
          // tn.style.margin = '1px';
          liveView.appendChild(tn);
        }
        // liveView.appendChild(document.createTextNode('tool.main(\n'));
        addTn('yourtool.main(new String[]{');
        [...(settings.getMainArgsOverride() ?? []), '/path/to/file.tmp'].forEach((part, partIdx) => {
          if (partIdx > 0) {
            liveView.appendChild(document.createTextNode(', '));
          }
          const span = document.createElement('span');
          span.innerText = part;
          span.style.whiteSpace = 'pre';
          span.style.border = '1px solid #888';
          span.style.marginLeft = '0.125rem';
          span.style.marginRight = '0.125rem';
          liveView.appendChild(span);
        });
        addTn('})')
        // liveView.innerText = `tool.main(\n  ${[...(settings.getMainArgsOverride() ?? []), '/path/to/tmp-file'].filter(Boolean).join(',\n  ')})`
      };
      liveView.classList.add('override-main-args-live-view');
      refreshLiveView();
      // elem.classList.add('input-Monaco');

      const editor = (window as any).monaco.editor.create(elem, {
        value: getArgs(),
        language: 'plaintext',
        // theme: 'dark',
        scrollBeyondLastLine: false,
        automaticLayout: true,
        minimap: {
          enabled: false,
        },
        wordWrap: true,
      });
      editor.onDidChangeModelContent(() => {
        console.log('settings args..', editor.getValue());
        const errs: { line: number, col: number, msg: string }[] = [];
        try {
          setArgs(editor.getValue(), (line, col, msg) => errs.push({line, col, msg }));
          refreshLiveView();
          onChange();
        } catch (e) {
          console.warn('Error when parsing user input', e);
        }

        (window as any).monaco.editor.setModelMarkers(
          editor.getModel(),
          'override-problems',
          errs.map(({line, col, msg }) => ({
            startLineNumber: line,
            startColumn: col,
            endLineNumber: line,
            endColumn: col + 1,
            message: msg,
            severity: 8, // default to 'error' (8)
          }))
        );

        console.log('did set args, getArgs():', getArgs(), '||', settings.getMainArgsOverride());
      });

      const wrapper = document.createElement('div');
      (wrapper.style as any) = `
      display: flex;
      width: 100%;
      height: 8rem;
      `
      wrapper.appendChild(elem);
      root.appendChild(wrapper);

      const explanation = document.createElement('p');
      explanation.style.marginTop = '0';
      explanation.innerText = [
        'Example invocation with current override value:'
      ].join('\n');
      root.appendChild(explanation);
      root.appendChild(liveView);
    },
    rootStyle: `
    min-width: 12rem;
    min-height: 4rem;
    max-width: 80vw;
    max-height: 80vh;
    overflow: auto;
    `,
    resizable: true,
  });
  const close = () => {
    setDisableEditButton(false);
    windowInstance.remove();
  };

  return {
    forceClose: () => close(),
  };
}

export default displayMainArgsOverrideModal;
