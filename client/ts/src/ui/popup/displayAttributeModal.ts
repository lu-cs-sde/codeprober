import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import displayProbeModal from "./displayProbeModal";
import showWindow from "../create/showWindow";
import displayArgModal from "./displayArgModal";
import formatAttr from "./formatAttr";
import createTextSpanIndicator from "../create/createTextSpanIndicator";

const displayAttributeModal = (env: ModalEnv, modalPos: ModalPosition | null, locator: NodeLocator) => {
  let filter: string = '';
  let attrs: AstAttr[] | null = null;
  let showErr = false;
  const popup = showWindow({
    pos: modalPos,
    rootStyle: `
      min-width: 16rem;
      min-height: 8rem;
      max-height: 32rem;
    `,
    render: (root, cancelToken) => {
      while (root.firstChild) root.firstChild.remove();
      // root.innerText = 'Loading..';

      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const headType = document.createElement('span');
          headType.classList.add('syntax-type');
          headType.innerText = `${locator.result.type}`;

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
          popup.remove();
        },
      }).element);

      if (!attrs && !showErr) {
        const spinner = createLoadingSpinner();
        spinner.classList.add('absoluteCenter');
        const spinnerWrapper = document.createElement('div');
        spinnerWrapper.style.height = '7rem' ;
        spinnerWrapper.style.display = 'block' ;
        spinnerWrapper.style.position = 'relative';
        spinnerWrapper.appendChild(spinner);
        root.appendChild(spinnerWrapper);
        return;
      }

      if (attrs) {
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
        setTimeout(() => filterInput.focus(), 50);
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
              return false;
            }
            // const formatted =
            return reg.test(formatAttr(attr));
          }
          const matches = attrs.filter(match);
          const misses = attrs.filter(a => !match(a));

          const showProbe = (attr: AstAttr) => {
            popup.remove();

            if (!attr.args || attr.args.length === 0) {
              displayProbeModal(env, popup.getPos(), locator, { name: attr.name });
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
            node.innerText = formatAttr(attr);
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
            // sortedAttrs.appendChild(document.createElement('hr'));
            // sortedAttrs.appendChild(document.createElement('hr'));
            // sortedAttrs.appendChild(document.createElement('hr'));
          }
          misses.forEach((attr, idx) => buildNode(attr, idx > 0, !matches.length && misses.length === 1));
        };
        resortList();
        root.appendChild(sortedAttrs);
      } else {
        // showErr

        // if (cancelToken.cancelled) { return; }
        while (root.firstChild) root.removeChild(root.firstChild);
        root.style.display = 'flex';
        root.style.justifyContent = 'center';
        root.style.padding = 'auto';
        root.style.textAlign = 'center';
        root.style.color = '#F88';
        root.innerText = 'Error while\nloading attributes..';
        setTimeout(() => popup.remove(), 1000);
      }
    },
  });

  env.performRpcQuery({
    attr: {
      name: 'pasta_pastaAttrs'
    },
    locator: locator,
  })
    .then((result: RpcResponse) => {
      // if (cancelToken.cancelled) { return; }
      const parsed = result.pastaAttrs;
      if (!parsed) {
        throw new Error('Unexpected response body "' + result + '"');
      }

      filter = '';

      attrs = [];
      const deudplicator = new Set();
      parsed.forEach(attr => {
        const uniqId = JSON.stringify(attr);
        if (deudplicator.has(uniqId)) {
          return;
        }
        deudplicator.add(uniqId);
        attrs?.push(attr);
      });
      // attrs = [...new Set(parsed)];
      popup.refresh();

    })
    .catch(err => {
      console.warn('UserPA err:', err);
      showErr = true;
      popup.refresh();
    });
}

export default displayAttributeModal;
