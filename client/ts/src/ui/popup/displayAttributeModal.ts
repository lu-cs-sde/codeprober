import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import displayProbeModal from "./displayProbeModal";
import showWindow from "../create/showWindow";
import displayArgModal from "./displayArgModal";
import formatAttr from "./formatAttr";
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import displayHelp from "./displayHelp";
import adjustLocator from "../../model/adjustLocator";
import settings from "../../settings";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import trimTypeName from "../trimTypeName";

const displayAttributeModal = (env: ModalEnv, modalPos: ModalPosition | null, locator: NodeLocator) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  let filter: string = '';
  let state: { type: 'attrs', attrs: AstAttr[] } | { type: 'err', body: RpcBodyLine[] } | null = null;

 let fetchState: 'idle' | 'fetching' | 'queued' = 'idle';
  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    popup.remove();
  };
  let isFirstRender = true;
  const popup = showWindow({
    pos: modalPos,
    rootStyle: `
      min-width: 16rem;
      min-height: 8rem;
      80vh;
    `,
    render: (root) => {
      while (root.firstChild) root.firstChild.remove();
      // root.innerText = 'Loading..';

      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const headType = document.createElement('span');
          headType.classList.add('syntax-type');
          headType.innerText = `${locator.result.label ?? trimTypeName(locator.result.type)}`;

          const headAttr = document.createElement('span');
          headAttr.classList.add('syntax-attr');
          headAttr.innerText = `.?`;
          // headAttr.style.fontStyle= 'italic';

          container.appendChild(headType);
          container.appendChild(headAttr);
          container.appendChild(createTextSpanIndicator({
            span: startEndToSpan(locator.result.start, locator.result.end),
            marginLeft: true,
            onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.result.start, locator.result.end) : null),
          }));
        },
        onClose: () => {
          cleanup();
        },
        extraActions: [
          {
            title: 'Help',
            invoke: () => {
              displayHelp('property-list-usage', () => {});
            }
          },
        ],
      }).element);

      const addSpinner = () => {
        const spinner = createLoadingSpinner();
        spinner.classList.add('absoluteCenter');
        const spinnerWrapper = document.createElement('div');
        spinnerWrapper.style.height = '7rem' ;
        spinnerWrapper.style.display = 'block' ;
        spinnerWrapper.style.position = 'relative';
        spinnerWrapper.appendChild(spinner);
        root.appendChild(spinnerWrapper);
      }

      if (state === null) {
        addSpinner();
        return;
      }

      if (state.type === 'err') {
        if (state.body.length === 0) {
          const text = document.createElement('span');
          text.classList.add('captured-stderr');
          // text.style.color = '#F88';
          text.innerText = `Failed listing properties`;
          root.appendChild(text);
          return;
        }
        root.appendChild(encodeRpcBodyLines(env, state.body));
      } else {

        const attrs = state.attrs;
        let resortList = () => {};
        let submit = () => {};

        const nodesList: HTMLDivElement[] = [];
        const filterInput = document.createElement('input');
        filterInput.placeholder = 'Filter';
        filterInput.classList.add('attr-modal-filter');
        if (!filter) {
          filterInput.classList.add('empty');
        }
        filterInput.type = 'text';
        filterInput.value = filter;
        filterInput.oninput = (e) => {
          filter = filterInput.value.trim();
          resortList();
        }
        filterInput.onkeydown = (e) => {
          // filterInput.scrollIntoView();
          if (e.key === 'Enter') {
            submit();
          } else if (e.key === 'ArrowDown') {
            if (nodesList.length > 0) {
              nodesList[0]?.focus();
              e.preventDefault();
            }
          }
        }
        if (isFirstRender) {
          isFirstRender = false;
          setTimeout(() => filterInput.focus(), 50);
        }
        root.appendChild(filterInput);

        root.style.minHeight = '4rem';

        const sortedAttrs = document.createElement('div');
        resortList = () => {
          nodesList.length = 0;
          submit = () => {};
          while (sortedAttrs.firstChild) sortedAttrs.firstChild.remove();
          if (!attrs) {
            console.log('attrs disappeared after a successful load??')
            return;
          }
          function escapeRegex(string: string) {
            return string.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
          }

          const reg = filter ? new RegExp(`.*${[...filter].map(part => part.trim()).filter(Boolean).map(part => escapeRegex(part)).join('.*')}.*`, 'i') : null;
          const match = (attr: AstAttr) => {
            if (!reg) {
              return !!attr.astChildName;
            }
            // const formatted =
            return reg.test(formatAttr(attr)) || (attr.astChildName && reg.test(attr.astChildName));
          }
          const matches = attrs.filter(match);
          const misses = attrs.filter(a => !match(a));

          const showProbe = (attr: AstAttr) => {
            cleanup();

            if (!attr.args || attr.args.length === 0) {
              displayProbeModal(env, popup.getPos(), locator, { name: attr.name });
            } else {
              if (attr.args.every(arg => arg.detail === 'OUTPUTSTREAM')) {
                // Shortcut directly to probe since there is nothing for user to add in arg modal
                displayProbeModal(env, popup.getPos(), locator, {
                  name: attr.name,
                  args: attr.args.map(arg => ({
                    ...arg,
                    value: null
                  })),
                });
              } else {
                displayArgModal(env, popup.getPos(), locator, {
                  name: attr.name,
                  args: attr.args.map(arg => ({
                    ...arg,
                    value: ''
                  })),
                });
              }

            }
          }
          const buildNode = (attr: AstAttr, borderTop: boolean, highlight: boolean) => {
            const node = document.createElement('div');
            const ourNodeIndex = nodesList.length;
            nodesList.push(node);
            node.tabIndex = 0;
            node.onmousedown = (e) => { e.stopPropagation(); }
            node.classList.add('syntax-attr-dim-focus');
            node.classList.add('clickHighlightOnHover');
            node.style.padding = '0 0.25rem';
            if (borderTop) {
              node.style.borderTop = `1px solid gray`;
            }
            if (highlight) {
              node.classList.add('bg-syntax-attr-dim');
            }
            node.appendChild(document.createTextNode(formatAttr(attr)));

            node.onclick = () => showProbe(attr);
            node.onkeydown = (e) => {
              if (e.key === 'Enter') {
                showProbe(attr);
              } else if (e.key === 'ArrowDown' && ourNodeIndex !== (nodesList.length - 1)) {
                e.preventDefault();
                nodesList[ourNodeIndex+1]?.focus();
              } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                if (ourNodeIndex > 0) {
                  nodesList[ourNodeIndex-1]?.focus();
                } else {
                  filterInput.focus();
                }
              }
            }
            sortedAttrs.appendChild(node);
          };


          matches.forEach((attr, idx) => buildNode(attr, idx > 0, matches.length === 1));
          if (matches.length && misses.length) {
            if (matches.length === 1) {
              const submitExpl = document.createElement('p');
              submitExpl.classList.add('syntax-attr');
              submitExpl.style.textAlign = 'center';
              submitExpl.innerText = 'Press enter to select';
              sortedAttrs.appendChild(submitExpl);
              submit = () => showProbe(matches[0]);
            }
            const sep = document.createElement('div');
            sep.classList.add('search-list-separator')
            sortedAttrs.appendChild(sep);
          }
          misses.forEach((attr, idx) => buildNode(attr, idx > 0, !matches.length && misses.length === 1));
        };
        resortList();
        root.appendChild(sortedAttrs);
      }

      if (fetchState !== 'idle') {
        const spinner = createLoadingSpinner();
        spinner.classList.add('absoluteCenter');
        root.appendChild(spinner);
      }
    },
  });
  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {
      adjusters.forEach(adj => adjustLocator(adj, locator));
    }
    fetchAttrs();
    popup.refresh();
  };

 const fetchAttrs = () => {
  switch (fetchState) {
    case 'idle': {
      fetchState = 'fetching'
      break;
    }
    case 'fetching': {
      fetchState = 'queued';
      return;
    }
    case 'queued': return;
  }

  env.performRpcQuery({
    attr: {
      name: settings.shouldShowAllProperties() ? 'meta:listAllProperties' : 'meta:listProperties'
    },
    locator,
  })
    .then((result: RpcResponse) => {
      const refetch = fetchState == 'queued';
      fetchState = 'idle';
      if (refetch) fetchAttrs();

      const parsed = result.properties;
      if (!parsed) {
        // root.appendChild(createTitle('err'));
        if (result.body?.length) {
          state = { type: 'err', body: result.body };
          popup.refresh();
          // root.appendChild(encodeRpcBodyLines(env, parsed.body));
          return;
        }
        throw new Error('Unexpected response body "' + JSON.stringify(result) + '"');
      }


      const uniq: AstAttr[] = [];
      const deudplicator = new Set();
      parsed.forEach(attr => {
        const uniqId = JSON.stringify(attr);
        if (deudplicator.has(uniqId)) {
          return;
        }
        deudplicator.add(uniqId);
        uniq.push(attr);
      });
      state = { type: 'attrs', attrs: uniq };
      popup.refresh();

    })
    .catch(err => {
      console.warn('Error when loading attributes', err);
      state = { type: 'err', body: [] };
      // showErr = true;
      popup.refresh();
    });
 };
 fetchAttrs();
}

export default displayAttributeModal;
