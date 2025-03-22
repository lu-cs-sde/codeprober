import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import displayProbeModal, { searchProbePropertyName } from "./displayProbeModal";
import displayArgModal from "./displayArgModal";
import formatAttr from "./formatAttr";
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import displayHelp from "./displayHelp";
import settings from "../../settings";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import trimTypeName from "../trimTypeName";
import displayAstModal from "./displayAstModal";
import ModalEnv from '../../model/ModalEnv';
import { Property, ListPropertiesReq, ListPropertiesRes, RpcBodyLine } from '../../protocol';
import startEndToSpan from '../startEndToSpan';
import UpdatableNodeLocator from '../../model/UpdatableNodeLocator';

interface OptionalArgs {
  initialFilter?: string;
}
const displayAttributeModal = (
  env: ModalEnv,
  modalPos: ModalPosition | null,
  locator: UpdatableNodeLocator,
  optionalArgs: OptionalArgs = {},
) => {
  const queryId = `attr-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  let filter: string = optionalArgs.initialFilter ?? '';
  let state: { type: 'attrs', attrs: Property[] } | { type: 'err', body: RpcBodyLine[] } | null = null;

 let fetchState: 'idle' | 'fetching' | 'queued' = 'idle';
  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    popup.remove();
  };
  let isFirstRender = true;
  const popup = env.showWindow({
    pos: modalPos,
    debugLabel: `attr:${locator.get().result.type}`,
    rootStyle: `
      min-width: 16rem;
      min-height: 8rem;
      80vh;
    `,
    onForceClose: cleanup,
    render: (root) => {
      while (root.firstChild) root.firstChild.remove();

      root.appendChild(createModalTitle({
        shouldAutoCloseOnWorkspaceSwitch: true,
        renderLeft: (container) => {
          if (env === env.getGlobalModalEnv()) {
            const headType = document.createElement('span');
            headType.classList.add('syntax-type');
            headType.innerText = `${locator.get().result.label ?? trimTypeName(locator.get().result.type)}`;
            container.appendChild(headType);
          }

          const headAttr = document.createElement('span');
          headAttr.classList.add('syntax-attr');
          headAttr.innerText = `.?`;

          container.appendChild(headAttr);
          container.appendChild(createTextSpanIndicator({
            span: startEndToSpan(locator.get().result.start, locator.get().result.end),
            marginLeft: true,
            onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.get().result.start, locator.get().result.end) : null),
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
          {
            title: 'Render AST downwards',
            invoke: () => {
              cleanup();
              displayAstModal(env, popup.getPos(), locator, 'downwards');
            }
          },
          {
            title: 'Render AST upwards',
            invoke: () => {
              cleanup();
              displayAstModal(env, popup.getPos(), locator, 'upwards');
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

        let attrs = state.attrs;
        const groupByAspect = settings.shouldGroupPropertiesByAspect();
        if (groupByAspect && attrs) {
          attrs = [...attrs].sort((a, b) => {
            if (!!a.astChildName != !!b.astChildName) {
              return a.astChildName ? -1 : 1;
            }
            if (!!a.aspect != !!b.aspect) {
              return a.aspect ? -1 : 1;
            }
            if (a.aspect) {
              const cmp = a.aspect.localeCompare(b.aspect || '', 'en-GB')
              return cmp || formatAttr(a).localeCompare(formatAttr(b), 'en-GB');
            }
            return 0;
          });
        }
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
          setTimeout(() => {
            if (optionalArgs.initialFilter) {
              filterInput.select();
            } else {
              filterInput.focus()
            }
          }, 50);
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
          const match = (attr: Property) => {
            if (!reg) {
              return !!attr.astChildName;
            }
            const combinedName = `${(groupByAspect ? (attr.aspect || 'No Aspect') : '')} ${attr.astChildName || ''} ${formatAttr(attr)}`;
            return reg.test(combinedName);
          }

          const matches: Property[] = [];
          const misses: Property[] = [];
          attrs.forEach(prop => {
            if (match(prop)) {
              matches.push(prop);
            } else {
              misses.push(prop);
            }
          })

          const showProbe = (attr: Property) => {
            cleanup();

            if (!attr.args || attr.args.length === 0) {
              displayProbeModal(env, popup.getPos(), locator, { name: attr.name }, {});
            } else {
              if (attr.args.every(arg => arg.type ==='outputstream')) {
                // Shortcut directly to probe since there is nothing for user to add in arg modal
                displayProbeModal(env, popup.getPos(), locator, attr, {});
              } else {
                displayArgModal(env, popup.getPos(), locator, attr, {});
              }
            }
          }
          const addAspectLabels = groupByAspect && attrs.some(attr => !!attr.aspect);
          let lastAspect = '';
          const buildNode = (attr: Property, borderTop: boolean, highlight: boolean) => {
            if (addAspectLabels) {
              const newAspect = attr.aspect || '';
              if (newAspect !== lastAspect) {
                // console
                const head = document.createElement('p');
                head.style.marginBottom = '0';
                head.style.marginTop = '0.5rem';
                head.style.fontSize = '0.75rem';
                head.classList.add('syntax-type');
                head.innerText = newAspect || 'No Aspect';
                sortedAttrs.appendChild(head);
                lastAspect = newAspect;
              }
            }
            const node = document.createElement('div');
            const ourNodeIndex = nodesList.length;
            nodesList.push(node);
            node.tabIndex = 0;
            node.onmousedown = (e) => { e.stopPropagation(); }
            node.style.whiteSpace = 'break-spaces';
            node.style.maxWidth = '100%';
            node.style.wordBreak = 'break-all';
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

          const addSubmitExplanation = (msg: string) => {
            const submitExpl = document.createElement('p');
            submitExpl.classList.add('syntax-attr');
            submitExpl.style.textAlign = 'center';
            submitExpl.innerText = msg;
            sortedAttrs.appendChild(submitExpl);
          };

          matches.forEach((attr, idx) => buildNode(attr, idx > 0, matches.length === 1));
          if (matches.length && misses.length) {
            if (matches.length === 1) {
              addSubmitExplanation('Press enter to select');
              submit = () => showProbe(matches[0]);
            }
            const sep = document.createElement('div');
            sep.classList.add('search-list-separator')
            sortedAttrs.appendChild(sep);
          } else if (!matches.length && (filter.startsWith('*.') || filter.startsWith('?')) && filter.length >= 3) {
            addSubmitExplanation('Press enter to create search probe');
            submit = () => {
              const args: Property['args'] = [];
              if (filter.startsWith('?')) {
                args.push({ type: 'string', value: '' });
                args.push({ type: 'string', value: filter.slice(1) });
              } else {
                let propAndPredicate = filter.slice('*.'.length);
                const predicateStart = propAndPredicate.indexOf('?');
                if (predicateStart === -1) {
                  args.push({ type: 'string', value: propAndPredicate });
                } else {
                  args.push({ type: 'string', value: propAndPredicate.slice(0, predicateStart) });
                  args.push({ type: 'string', value: propAndPredicate.slice(predicateStart + 1) });
                }
              }
              const prop: Property = { name: searchProbePropertyName, args };
              cleanup();
              displayProbeModal(env, popup.getPos(), locator, prop, {});
            };
            const sep = document.createElement('div');
            sep.classList.add('search-list-separator')
            sortedAttrs.appendChild(sep);
          }
          lastAspect = '';
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
  const refresher = env.createCullingTaskSubmitter();
  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {
      locator.adjust(adjusters);
    }
    refresher.submit(() => {
      fetchAttrs();
      popup.refresh();
    });
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

  env.performTypedRpc<ListPropertiesReq, ListPropertiesRes>({
    locator: locator.get(),
    src: env.createParsingRequestData(),
    type: 'ListProperties',
    all: settings.shouldShowAllProperties(),
  })
    .then((result) => {
      const refetch = fetchState == 'queued';
      fetchState = 'idle';
      if (refetch) fetchAttrs();

      const parsed = result.properties;
      if (!parsed) {
        if (result.body?.length) {
          state = { type: 'err', body: result.body };
          popup.refresh();
          return;
        }
        throw new Error('Unexpected response body "' + JSON.stringify(result) + '"');
      }

      const uniq: Property[] = [];
      const deudplicator = new Set();
      parsed.forEach(attr => {
        const uniqId = JSON.stringify(attr);
        if (deudplicator.has(uniqId)) {
          return;
        }
        deudplicator.add(uniqId);
        uniq.push(attr);
      });
      state = { type: 'attrs', attrs: uniq };
      popup.refresh();

    })
    .catch(err => {
      console.warn('Error when loading attributes', err);
      state = { type: 'err', body: [] };
      popup.refresh();
    });
 };
 fetchAttrs();
}

export default displayAttributeModal;
