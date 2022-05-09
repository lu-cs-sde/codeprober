import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import displayAttributeModal from "./displayAttributeModal";
import showModal from "../create/showWindow";
import registerOnHover from "../create/registerOnHover";
import formatAttr from "./formatAttr";
import displayArgModal from "./displayArgModal";
import registerNodeSelector from "../create/registerNodeSelector";
import adjustLocator from "../../model/adjustLocator";
import displayHelp from "./displayHelp";

const displayProbeModal = (env: ModalEnv, modalPos: ModalPosition, locator: NodeLocator, attr: AstAttrWithValue) => {
  console.log('dPM, env:', env);
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  const localErrors: ProbeMarker[] = [];
  env.probeMarkers[queryId] = localErrors;
  // const stickyMarker = env.registerStickyMarker(span)

  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    delete env.probeMarkers[queryId];
    delete env.probeWindowStateSavers[queryId];
    env.triggerWindowSave();
    if (localErrors.length > 0) {
      env.updateMarkers();
    }
    // stickyMarker.remove();
  };

  let copyBody: RpcBodyLine[] = [];

  const createTitle = () => {
    return createModalTitle({
      extraActions: [
        {
          title: 'Duplicate window',
          invoke: () => {
            const pos = queryWindow.getPos();
            displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, JSON.parse(JSON.stringify(locator)), attr);
          },
        },
        {
          title: 'Copy input to clipboard',
          invoke: () => {
            navigator.clipboard.writeText(JSON.stringify({ locator, attr }, null, 2));
          }
        },
        {
          title: 'Copy output to clipboard',
          invoke: () => {
            navigator.clipboard.writeText(copyBody.map(line => typeof line === 'string' ? line : JSON.stringify(line, null, 2)).join('\n'));
          }
        },
        {
          title: 'General probe help',
          invoke: () => {
            displayHelp('probe-window', () => {});
          }
        },
        {
          title: 'Magic output messages help',
          invoke: () => {
            displayHelp('magic-stdout-messages', () => {});
          }
        },
      ],
      // onDuplicate: () => {
      //   const pos = queryWindow.getPos();
      //   displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, JSON.parse(JSON.stringify(locator)), attr);
      // },
      renderLeft: (container) => {
        const headType = document.createElement('span');
        headType.classList.add('syntax-type');
        headType.innerText = `${locator.result.type}`;

        const headAttr = document.createElement('span');
        headAttr.classList.add('syntax-attr');
        headAttr.classList.add('clickHighlightOnHover');
        if (!attr.args || attr.args.length === 0) {
          headAttr.innerText = `.${formatAttr(attr)}`;
        } else {
          headAttr.appendChild(document.createTextNode(`.${attr.name}(`));
          attr.args.forEach((arg, argIdx) => {
            if (argIdx > 0) {
              headAttr.appendChild(document.createTextNode(`,`));
            }
            switch (arg.type) {
              case 'java.lang.String': {
                const node = document.createElement('span');
                node.classList.add('syntax-string');
                node.innerText = `"${arg.value}"`;
                headAttr.appendChild(node);
                break;
              }
              case 'int': {
                const node = document.createElement('span');
                node.classList.add('syntax-int');
                node.innerText = `${arg.value}`;
                headAttr.appendChild(node);
                break;
              }
              case 'boolean': {
                const node = document.createElement('span');
                node.classList.add('syntax-modifier');
                node.innerText = `${arg.value}`;
                headAttr.appendChild(node);
                break;
              }
              default: {
                if (arg.isNodeType) {
                  const node = document.createElement('span');
                  node.classList.add('syntax-type');
                  if (!arg.value || (typeof arg.value !== 'object')) {
                    // Probably null
                    node.innerText = `${arg.value}`;
                  } else {
                    node.innerText = arg.value.result.type;
                  }
                  headAttr.appendChild(node);
                } else {
                  console.warn('Unsure of how to render', arg.type);
                  headAttr.appendChild(document.createTextNode(`${arg.value}`));
                }
                break;
              }
            }
          });
          headAttr.appendChild(document.createTextNode(`)`));
        }
        headAttr.onmousedown = (e) => { e.stopPropagation(); }
        headAttr.onclick = (e) => {
          if (env.duplicateOnAttr()) {
            displayAttributeModal(env, null, JSON.parse(JSON.stringify(locator)));
          } else {
            queryWindow.remove();
            cleanup();
            displayAttributeModal(env, queryWindow.getPos(), locator);
          }
          e.stopPropagation();
        }

        container.appendChild(headType);
        container.appendChild(headAttr);

        // TODO add edit pen here?

        if (attr.args?.length) {
          const editButton = document.createElement('img');
          editButton.src = '/icons/edit_white_24dp.svg';
          editButton.classList.add('modalEditButton');
          editButton.classList.add('clickHighlightOnHover');
          editButton.onmousedown = (e) => { e.stopPropagation(); }
          editButton.onclick = () => {
            queryWindow.remove();
            cleanup();
            displayArgModal(env, queryWindow.getPos(), locator, attr);
          };
          // const editHolder = document.createElement('div');
          // editHolder.style.display = 'flex';
          // editHolder.style.flexDirection = 'column';
          // editHolder.style.justifyContent = 'space-around';
          // editHolder.appendChild(editButton);

          container.appendChild(editButton);
        }

        const spanIndicator = createTextSpanIndicator({
          span: startEndToSpan(locator.result.start, locator.result.end),
          marginLeft: true,
          onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.result.start, locator.result.end) : null),
        });
        registerNodeSelector(spanIndicator, () => locator);
        container.appendChild(spanIndicator);
      },
      onClose: () => {
        queryWindow.remove();
        cleanup();
      },
    });
  };
  let lastSpinner: HTMLElement | null = null;
  let isFirstRender = true;
  let loading = false;
  let refreshOnDone = false;
  const queryWindow = showModal({
    pos: modalPos,
    rootStyle: `
      min-width: 20rem;
      min-height: fit-content;
    `,
    resizable: true,
    onFinishedMove: () => env.triggerWindowSave(),
    render: (root, cancelToken) => {
      if (lastSpinner != null) {
        lastSpinner.style.display = 'inline-block';
        lastSpinner = null;
      } else if (isFirstRender) {
        isFirstRender = false;
        while (root.firstChild) root.removeChild(root.firstChild);
        root.appendChild(createTitle().element);
        const spinner = createLoadingSpinner();
        spinner.classList.add('absoluteCenter');
        const spinnerWrapper = document.createElement('div');
        spinnerWrapper.style.height = '7rem' ;
        spinnerWrapper.style.display = 'block' ;
        spinnerWrapper.style.position = 'relative';
        spinnerWrapper.appendChild(spinner);
        root.appendChild(spinnerWrapper);
      }
      loading = true;

      if (!locator) {
        console.error('no locator??');
      }
      console.log('req attr:', JSON.stringify(attr, null, 2));
      env.performRpcQuery({
        attr,
        locator,
      })
        .then((parsed: RpcResponse) => {
          const body = parsed.body;
          copyBody = body;
          loading = false;
          if (refreshOnDone) {
            refreshOnDone = false;
            queryWindow.refresh();
          }
          if (cancelToken.cancelled) { return; }
          while (root.firstChild) root.removeChild(root.firstChild);

          let refreshMarkers = localErrors.length > 0;
          localErrors.length = 0;

          console.log('probe errors:', parsed.errors);
          (parsed.errors as { severity: ('error' | 'warning' | 'info') ; start: number; end: number; msg: string }[]).forEach(({severity, start: errStart, end: errEnd, msg }) => {
            localErrors.push({ severity, errStart, errEnd, msg });
          })
          console.log('parsed.loc?', parsed.locator);
          const updatedArgs = parsed.args;
          if (updatedArgs) {
            refreshMarkers = true;
            attr.args?.forEach((arg, argIdx) => {
              arg.type = updatedArgs[argIdx].type;
              arg.isNodeType = updatedArgs[argIdx].isNodeType;
              console.log('updating arg value from', arg.value, 'to', updatedArgs[argIdx].value);
              arg.value = updatedArgs[argIdx].value;
            })
          }
          if (parsed.locator) {
            refreshMarkers = true;
            locator = parsed.locator;
          }
          if (refreshMarkers || localErrors.length > 0) {
            console.log('refresh markers!! local:', localErrors);
            env.updateMarkers();
          }
          const titleRow = createTitle();
          root.append(titleRow.element);

          const encodeLine = (target: HTMLElement, line: RpcBodyLine, respectIndent = false) => {
            if (typeof line === 'string') {
              const trimmed = line.trimStart();
              if (trimmed.length !== line.length) {
                target.appendChild(document.createTextNode(' '.repeat(line.length - trimmed.length)));
              }
              if (line.trim()) {
                target.appendChild(document.createTextNode(line.trim()));
              }
              target.appendChild(document.createElement('br'));
            } else if (Array.isArray(line)) {
              if (!respectIndent) {
                // First level indent, 'inline' it
                line.forEach(sub => encodeLine(target, sub, true));
              } else {
                // >=2 level indent, respect it
                const deeper = document.createElement('pre');
                // deeper.style.borderLeft = '1px solid #88888877';
                deeper.style.marginLeft = '1rem';
                deeper.style.marginTop = '0.125rem';
                line.forEach(sub => encodeLine(deeper, sub, true));
                target.appendChild(deeper);
              }
            } else {
              switch (line.type) {
                case "stdout": {
                  const span = document.createElement('span');
                  span.classList.add('captured-stdout');
                  span.innerText = `> ${line.value}`;
                  target.appendChild(span);
                  target.appendChild(document.createElement('br'));
                  break;
                }
                case "stderr": {
                  const span = document.createElement('span');
                  span.classList.add('captured-stderr');
                  span.innerText = `> ${line.value}`;
                  target.appendChild(span);
                  target.appendChild(document.createElement('br'));
                  break;
                }
                case "node": {
                  const { start, end, type } = line.value.result;
                  console.log('node line:', JSON.stringify(line, null, 2));

                  const container = document.createElement('div');
                  const span: Span = {
                    lineStart: (start >>> 12), colStart: (start & 0xFFF),
                    lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
                  };

                  container.appendChild(createTextSpanIndicator({
                    span,
                    marginLeft: false,
                  }));
                  const typeNode = document.createElement('span');
                  typeNode.classList.add('syntax-type');
                  typeNode.innerText = type;
                  container.appendChild(typeNode);

                  container.classList.add('clickHighlightOnHover');
                  container.style.width = 'fit-content';
                  container.style.display = 'inline';
                  registerOnHover(container, on => {
                    env.updateSpanHighlight(on ? span : null)
                  });
                  container.onmousedown = (e) => {
                    e.stopPropagation();
                  }
                  registerNodeSelector(container, () => line.value);
                  container.addEventListener('click', () => {
                    displayAttributeModal(env, null, line.value);
                  });
                  target.appendChild(container);
                  break;
                }

                default: {
                  console.warn('Unknown body line type', line);
                  break;
                }
              }
            }
          }

          const pre = document.createElement('pre');
          pre.style.margin = '0px';
          pre.style.padding = '0.5rem';
          pre.style.fontSize = '0.75rem';
          // pre.innerHtml = lines.slice(outputStart + 1).join('\n').trim();
          body
            .filter((line, lineIdx, arr) => {
              // Keep empty lines only if they are followed by a non-empty line
              // Removes two empty lines in a row, and removes trailing empty lines
              if (!line && !arr[lineIdx+1]) { return false; }
              return true;
            })
            .forEach((line) => {
              encodeLine(pre, line);
              // '\n'
            });
          root.appendChild(pre);

          const spinner = createLoadingSpinner();
          spinner.style.display = 'none';
          spinner.classList.add('absoluteCenter');
          lastSpinner = spinner;
          root.appendChild(spinner);
        })
        .catch(err => {
          loading = false;
          if (refreshOnDone) {
            refreshOnDone = false;
            queryWindow.refresh();
            return;
          }
          if (cancelToken.cancelled) { return; }
          console.log('ProbeModal RPC catch', err);
          root.innerHTML = '';
          root.innerText = 'Failed refreshing query..';
          setTimeout(() => {
            queryWindow.remove();
          }, 1000);
        })
    },
  });
  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {

      adjusters.forEach(adj => adjustLocator(adj, locator));
      attr.args?.forEach(({ value }) => {
        if (value && typeof value === 'object') {
          console.log('adjusting value: ', value);
          adjusters.forEach(adj => adjustLocator(adj, value));
        }
      })
      // console.log('Adjusted span to:', span);
    }
    if (loading) {
      refreshOnDone = true;
    } else {
      queryWindow.refresh();
    }
  }

  env.probeWindowStateSavers[queryId] = (target) => target.push({ locator, attr, modalPos: queryWindow.getPos() });
  env.triggerWindowSave();
}

export default displayProbeModal;
