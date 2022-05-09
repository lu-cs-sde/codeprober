import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import displayAttributeModal from "./displayAttributeModal";
import registerOnHover from "../create/registerOnHover";
import showWindow from "../create/showWindow";
import registerNodeSelector from "../create/registerNodeSelector";

const displayRagModal = (env: ModalEnv, line: number, col: number) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    popup.remove();
  };

  const popup = showWindow({
    rootStyle: `
        min-width: 12rem;
        min-height: 4rem;
        max-height: 32rem;
      `,
    render: (root, cancelToken) => {
      while (root.firstChild) {
        root.firstChild.remove();
      }
      // root.innerText = 'Loading..';
      root.style.display = 'contents';
      const spinner = createLoadingSpinner();
      // spinner.style.width = '16rem';
      // spinner.style.height = '4rem';
      spinner.classList.add('absoluteCenter');
      root.appendChild(spinner);

      const rootProgramLocator: TypeAtLoc = {
        type: '?',
        start: (line << 12) + col,
        end: (line << 12) + col
      };
      env.performRpcQuery({
        attr: {
          name: 'pasta_spansAndNodeTypes',
        },
        locator: {
          // root: rootProgramLocator,
          result: rootProgramLocator,
          steps: []
        },
      })
        .then((parsed: RpcResponse) => {
          if (cancelToken.cancelled) { return; }
          while (root.firstChild) root.removeChild(root.firstChild);
          root.style.minHeight = '4rem';

          root.appendChild(createModalTitle({
            renderLeft: (container) => {
              const headType = document.createElement('span');
              headType.classList.add('syntax-stype');
              headType.innerText = `Select node..`;
              container.appendChild(headType);
            },
            onClose: () => {
              cleanup();
            },
          }).element);

          // const needle = 'SpansAndNodeTypes :: ';

          if (!parsed.spansAndNodeTypes) {
            throw new Error("Couldn't find expected line in output");
          }
          const rowsContainer = document.createElement('div');
          rowsContainer.style.padding = '2px';
          root.appendChild(rowsContainer);
          // const parsed = JSON.parse(interestingLine.slice(needle.length)) as string[];
          parsed.spansAndNodeTypes.forEach(({ start, end, type }, entIdx) => {
            const span = { lineStart: (start >>> 12), colStart: (start & 0xFFF), lineEnd: (end >>> 12), colEnd: (end & 0xFFF) };
            const node = document.createElement('div');
            node.classList.add('clickHighlightOnHover');
            node.style.padding = `0 0.25rem`;
            if (entIdx !== 0) {
              node.style.borderTop = '1px solid gray';
            }
            node.innerText = `${type}${start === 0 && end === 0 ? ` ⚠️<No position>` : ''}`;
            registerOnHover(node, on => env.updateSpanHighlight(on ? span : null));
            node.onmousedown = (e) => { e.stopPropagation(); }

            const nodeTal: TypeAtLoc = { 
              start: (span.lineStart << 12) + span.colStart,
              end: (span.lineEnd << 12) + span.colEnd,
              type,
            }
            const locator: NodeLocator = { result: nodeTal, steps: [{ type: 'tal', value: nodeTal }]};
            registerNodeSelector(node, () => locator );
            node.onclick = () => {
              cleanup();
              env.updateSpanHighlight(null);
              displayAttributeModal(env, popup.getPos(), locator);
            };
            rowsContainer.appendChild(node);
          });
        })
        .catch(err => {
          if (cancelToken.cancelled) { return; }
          console.warn('query failed', err);
          while (root.firstChild) root.removeChild(root.firstChild);
          root.style.display = 'flex';
          root.style.justifyContent = 'center';
          root.style.padding = 'auto';
          root.style.textAlign = 'center';
          root.style.color = '#F88';
          root.innerText = 'No AST node\nat this location..';
          setTimeout(() => cleanup(), 1000);
        })
    }
  });

  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {
      adjusters.forEach((adj) => {
        const [l,c] = adj(line, col);
        line = l;
        col = c;
      });
    }
    popup.refresh();
  }
};

export default displayRagModal;
