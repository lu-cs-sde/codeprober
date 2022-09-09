import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import displayAttributeModal from "./displayAttributeModal";
import showModal from "../create/showWindow";
import formatAttr from "./formatAttr";
import displayArgModal from "./displayArgModal";
import registerNodeSelector from "../create/registerNodeSelector";
import adjustLocator from "../../model/adjustLocator";
import displayHelp from "./displayHelp";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import trimTypeName from "../trimTypeName";

const displayProbeModal = (env: ModalEnv, modalPos: ModalPosition, locator: NodeLocator, attr: AstAttrWithValue) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  const localErrors: ProbeMarker[] = [];
  env.probeMarkers[queryId] = localErrors;
  // const stickyMarker = env.registerStickyMarker(span)

  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    delete env.probeMarkers[queryId];
    delete env.probeWindowStateSavers[queryId];
    env.currentlyLoadingModals.delete(queryId);
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
        headType.innerText = `${trimTypeName(locator.result.type)}`;

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
          if (env.duplicateOnAttr() != e.shiftKey) {
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
      min-width: 16rem;
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
      // console.log('req attr:', JSON.stringify(attr, null, 2));

      env.currentlyLoadingModals.add(queryId);
      const rpcQueryStart = performance.now();
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
          if (typeof parsed.totalTime === 'number'
            && typeof parsed.parseTime === 'number'
            && typeof parsed.createLocatorTime === 'number'
            && typeof parsed.applyLocatorTime === 'number'
            && typeof parsed.attrEvalTime === 'number' ) {
            env.statisticsCollector.addProbeEvaluationTime({
              attrEvalMs: parsed.attrEvalTime / 1_000_000.0,
              fullRpcMs: Math.max(performance.now() - rpcQueryStart),
              serverApplyLocatorMs: parsed.applyLocatorTime / 1_000_000.0,
              serverCreateLocatorMs: parsed.createLocatorTime / 1_000_000.0,
              serverParseOnlyMs: parsed.parseTime / 1_000_000.0,
              serverSideMs: parsed.totalTime / 1_000_000.0,
            });
          }
          if (cancelToken.cancelled) { return; }
          if (!refreshOnDone) {
            env.currentlyLoadingModals.delete(queryId);
          }
          while (root.firstChild) root.removeChild(root.firstChild);

          let refreshMarkers = localErrors.length > 0;
          localErrors.length = 0;

          // console.log('probe errors:', parsed.errors);
          (parsed.errors as { severity: ('error' | 'warning' | 'info') ; start: number; end: number; msg: string }[]).forEach(({severity, start: errStart, end: errEnd, msg }) => {
            localErrors.push({ severity, errStart, errEnd, msg });
          })
          const updatedArgs = parsed.args;
          if (updatedArgs) {
            refreshMarkers = true;
            attr.args?.forEach((arg, argIdx) => {
              arg.type = updatedArgs[argIdx].type;
              arg.isNodeType = updatedArgs[argIdx].isNodeType;
              arg.value = updatedArgs[argIdx].value;
            })
          }
          if (parsed.locator) {
            refreshMarkers = true;
            locator = parsed.locator;
          }
          if (refreshMarkers || localErrors.length > 0) {
            env.updateMarkers();
          }
          const titleRow = createTitle();
          root.append(titleRow.element);

          root.appendChild(encodeRpcBodyLines(env, body));

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
          env.currentlyLoadingModals.delete(queryId);
          console.log('ProbeModal RPC catch', err);
          root.innerHTML = '';
          root.innerText = 'Failed refreshing probe..';
          console.warn('Failed refreshing probe w/ args:', JSON.stringify({ locator, attr }, null, 2));
          setTimeout(() => { // TODO remove this again, once the title bar is added so we can close it
            queryWindow.remove();
            cleanup();
          }, 2000);
        })
    },
  });
  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {

      adjusters.forEach(adj => adjustLocator(adj, locator));
      attr.args?.forEach(({ value }) => {
        if (value && typeof value === 'object') {
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
