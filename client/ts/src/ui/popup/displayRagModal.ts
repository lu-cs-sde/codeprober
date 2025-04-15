import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import displayAttributeModal from "./displayAttributeModal";
import registerOnHover from "../create/registerOnHover";
import showWindow from "../create/showWindow";
import registerNodeSelector from "../create/registerNodeSelector";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import trimTypeName from "../trimTypeName";
import ModalEnv from '../../model/ModalEnv';
import { ListNodesReq, ListNodesRes } from '../../protocol';
import { createMutableLocator } from '../../model/UpdatableNodeLocator';
import SourcedDiagnostic from '../../model/SourcedDiagnostic';

const displayRagModal = (env: ModalEnv, line: number, col: number) => {
  const queryId = `rag-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  const localDiagnostics: SourcedDiagnostic[] = [];
  env.probeMarkers[queryId] = localDiagnostics;

  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    delete env.probeMarkers[queryId];
    popup.remove();
    env.updateMarkers();
  };

  const popup = showWindow({
    rootStyle: `
        min-width: 12rem;
        min-height: 4rem;
      `,
    onForceClose: cleanup,
    render: (root, { cancelToken }) => {
      root.style.display = 'contents';
      const spinner = createLoadingSpinner();
      spinner.classList.add('absoluteCenter');
      root.appendChild(spinner);

      const createTitle = (status: 'ok' | 'err') => createModalTitle({
        shouldAutoCloseOnWorkspaceSwitch: true,
        renderLeft: (container) => {
          const headType = document.createElement('span');
          headType.classList.add('syntax-stype');
          headType.innerText =  status === 'ok' ? 'Select node..' : `⚠️ Node listing failed..`;
          container.appendChild(headType);
        },
        onClose: () => {
          cleanup();
        },
      }).element;

      env.performTypedRpc<ListNodesReq, ListNodesRes>({
        src: env.createParsingRequestData(),
        pos: (line << 12) + col,
        type: 'ListNodes',
      })
        .then((parsed) => {
          if (cancelToken.cancelled) { return; }
          while (root.firstChild) root.removeChild(root.firstChild);
          root.style.minHeight = '4rem';

          let shouldRefreshMarkers = localDiagnostics.length > 0;
          localDiagnostics.length = 0;
          localDiagnostics.push(...(parsed.errors ?? []).map((err): SourcedDiagnostic => {
            return ({ ...err, source: 'Node Listing' });
          }));
          if (shouldRefreshMarkers || localDiagnostics.length > 0) {
            env.updateMarkers();
          }

          if (!parsed.nodes) {
            root.appendChild(createTitle('err'));
            if (parsed.body?.length) {
              root.appendChild(encodeRpcBodyLines(env, parsed.body));
              return;
            }
            throw new Error(`Couldn't find expected line or body in output '${JSON.stringify(parsed)}'`);
          }
          root.appendChild(createTitle('ok'));
          const rowsContainer = document.createElement('div');
          rowsContainer.style.padding = '2px';
          root.appendChild(rowsContainer);
          parsed.nodes.forEach((locator, entIdx) => {
            const { start, end, type, label } = locator.result;
            const span = { lineStart: (start >>> 12), colStart: (start & 0xFFF), lineEnd: (end >>> 12), colEnd: (end & 0xFFF) };
            const node = document.createElement('div');
            node.classList.add('clickHighlightOnHover');
            node.style.padding = `0 0.25rem`;
            if (entIdx !== 0) {
              node.style.borderTop = '1px solid gray';
            }
            node.innerText = `${label ?? trimTypeName(type)}${start === 0 && end === 0 ? ` ⚠️<No position>` : ''}`;
            registerOnHover(node, on => env.updateSpanHighlight(on ? span : null));
            node.onmousedown = (e) => { e.stopPropagation(); }

            registerNodeSelector(node, () => locator );
            node.onclick = () => {
              cleanup();
              env.updateSpanHighlight(null);
              displayAttributeModal(env, popup.getPos(), createMutableLocator(locator));
            };
            rowsContainer.appendChild(node);
          });
        })
        .catch(err => {
          if (cancelToken.cancelled) { return; }
          // TODO handle this better, show an informative, refresh-aware modal that doesn't autoclose
          // When starting it might be nice to open a modal and then tinker with settings until it refreshes successfully
          console.warn('query failed', err);
          while (root.firstChild) root.removeChild(root.firstChild);


          root.appendChild(createTitle('err'));

          const errMsg = document.createElement('div');


          errMsg.style.display = 'flex';
          errMsg.style.justifyContent = 'center';
          errMsg.style.padding = 'auto';
          errMsg.style.textAlign = 'center';
          errMsg.style.color = '#F88';
          errMsg.style.padding = '0.25rem';
          errMsg.innerText = 'Parsing failed..\nPerhaps a custom file suffix\nor main args override would help?\nLook at your terminal for more information.';
          root.appendChild(errMsg);
          // setTimeout(() => cleanup(), 1000);
        })
    }
  });

  const refresher = env.createCullingTaskSubmitter();
  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {
      adjusters.forEach((adj) => {
        const [l,c] = adj(line, col);
        line = l;
        col = c;
      });
    }
    refresher.submit(() => popup.refresh());
  }
};

export default displayRagModal;
