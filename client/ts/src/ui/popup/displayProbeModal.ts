import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import showWindow from "../create/showWindow";
import adjustLocator from "../../model/adjustLocator";
import displayHelp from "./displayHelp";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import createStickyHighlightController from '../create/createStickyHighlightController';
import ModalEnv from '../../model/ModalEnv';
import displayTestAdditionModal from './displayTestAdditionModal';
import renderProbeModalTitleLeft from '../renderProbeModalTitleLeft';
import settings from '../../settings';

const displayProbeModal = (env: ModalEnv, modalPos: ModalPosition | null, locator: NodeLocator, attr: AstAttrWithValue) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  const localErrors: ProbeMarker[] = [];
  env.probeMarkers[queryId] = localErrors;
  const stickyController = createStickyHighlightController(env);
  let lastOutput: RpcBodyLine[] = [];
  // const stickyMarker = env.registerStickyMarker(span)

  // if (attr.name == 'bytecodes') {
  //   setTimeout(() => displayTestAdditionModal(env, queryWindow.getPos(), locator, attr, lastOutput), 500);
  // }
  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    delete env.probeMarkers[queryId];
    delete env.probeWindowStateSavers[queryId];
    env.currentlyLoadingModals.delete(queryId);
    env.triggerWindowSave();
    if (localErrors.length > 0) {
      env.updateMarkers();
    }
    stickyController.cleanup();
  };

  let copyBody: RpcBodyLine[] = [];

  const createTitle = () => {
    return createModalTitle({
      extraActions: [
        {
          title: 'Duplicate window',
          invoke: () => {
            const pos = queryWindow.getPos();
            displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, JSON.parse(JSON.stringify(locator)), attr);
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
        ...[
          settings.shouldEnableTesting() && {
            title: 'Save as test',
            invoke: () => {
              displayTestAdditionModal(env, queryWindow.getPos(), locator, attr, lastOutput);
            },
          }
        ].filter(Boolean) as any
        ,
      ],
      // onDuplicate: () => {
      //   const pos = queryWindow.getPos();
      //   displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, JSON.parse(JSON.stringify(locator)), attr);
      // },
      renderLeft: (container) => renderProbeModalTitleLeft(
        env, container,
        () => {
          queryWindow.remove();
          cleanup();
        },
        () => queryWindow.getPos(),
        stickyController, locator, attr,
      ),
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
  const queryWindow = showWindow({
    pos: modalPos,
    rootStyle: `
      min-width: 16rem;
      min-height: fit-content;
    `,
    resizable: true,
    onFinishedMove: () => env.triggerWindowSave(),
    render: (root, { cancelToken }) => {
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

          parsed.errors.forEach(({severity, start: errStart, end: errEnd, msg }) => {
            localErrors.push({ severity, errStart, errEnd, msg });
          })
          const updatedArgs = parsed.args;
          if (updatedArgs) {
            refreshMarkers = true;
            attr.args?.forEach((arg, argIdx) => {
              arg.type = updatedArgs[argIdx].type;
              arg.detail = updatedArgs[argIdx].detail;
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

          lastOutput = body;
          root.appendChild(encodeRpcBodyLines(env, body, null));

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
  const refresher = env.createCullingTaskSubmitter();
  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {

      adjusters.forEach(adj => adjustLocator(adj, locator));
      attr.args?.forEach(({ value }) => {
        if (value && typeof value === 'object') {
          adjusters.forEach(adj => adjustLocator(adj, value));
        }
      })
    }
    if (loading) {
      refreshOnDone = true;
    } else {
      refresher.submit(() => queryWindow.refresh());
    }
  }

  env.probeWindowStateSavers[queryId] = (target) => {
    target.push({
      modalPos: queryWindow.getPos(),
      data: {
        type: 'probe',
        locator,
        attr,
      },
    });
  };
  env.triggerWindowSave();
}

export default displayProbeModal;
