import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import showWindow from "../create/showWindow";
import adjustLocator from "../../model/adjustLocator";
import displayHelp from "./displayHelp";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import createStickyHighlightController from '../create/createStickyHighlightController';
import ModalEnv, { JobId } from '../../model/ModalEnv';
import displayTestAdditionModal from './displayTestAdditionModal';
import renderProbeModalTitleLeft from '../renderProbeModalTitleLeft';
import settings from '../../settings';

const displayProbeModal = (env: ModalEnv, modalPos: ModalPosition | null, locator: NodeLocator, attr: AstAttrWithValue) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  const localErrors: ProbeMarker[] = [];
  env.probeMarkers[queryId] = localErrors;
  const stickyController = createStickyHighlightController(env);
  let lastOutput: RpcBodyLine[] = [];
  let activelyLoadingJob: JobId | null = null;
  let loading = false;
  let isCleanedUp = false;

  const stopJob = (jobId: JobId) => env.performRpcQuery({
    attr: { name: 'meta:stopJob' },
    locator: null as any,
  }, { jobId }).then(res => res.result === 'stopped');

  const cleanup = () => {
    isCleanedUp = true;
    delete env.onChangeListeners[queryId];
    delete env.probeMarkers[queryId];
    delete env.probeWindowStateSavers[queryId];
    env.currentlyLoadingModals.delete(queryId);
    env.triggerWindowSave();
    if (localErrors.length > 0) {
      env.updateMarkers();
    }
    stickyController.cleanup();
    console.log('cleanup: ', { loading, activelyLoadingJob});
    if (loading && activelyLoadingJob !== null) {
      stopJob(activelyLoadingJob);
    }
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

      const doFetch = () => new Promise<RpcResponse | 'stopped'>(async (resolve, reject) => {
        let isDone = false;
        let isConnectedToConcurrentCapableServer = false;
        let statusPre: HTMLElement | null = null;
        let localConcurrentCleanup = () => {};
        setTimeout(() => {
          if (isDone || isCleanedUp || !isConnectedToConcurrentCapableServer) {
            console.log('not polling status, due to ', { isDone, isConnectedToConcurrentCapableServer });
            return;
          }
          const stop = document.createElement('button');
          stop.innerText = 'Stop';
          stop.onclick = () => {
            stopJob(jobId).then(stopped => {
              if (stopped) {
                isDone = true;
                resolve('stopped');
              }
              // Else, job might have finished just as the user clicked stop
            });
          }
          root.appendChild(stop);

          statusPre = document.createElement('p');
          statusPre.style.whiteSpace = 'pre';
          statusPre.style.fontFamily = 'monospace';
          root.appendChild(statusPre);
          statusPre.innerText = `Request takes a while, polling status..\nIf you see message for longer than a few milliseconds then the job hasn't started running yet, or the server is severely overloaded.`;

          localConcurrentCleanup = () => {
            root.removeChild(stop);
            if (statusPre) { root.removeChild(statusPre); }
          };
          const poll = () => {
            if (isDone || isCleanedUp) {
              return;
            }
            env.performRpcQuery({
              attr: { name: 'meta:checkJobStatus' },
              locator: null as any,
            }, { jobId })
              .then(res => {
                console.log('rpc query for job status:', res)
                setTimeout(poll, 1000);
              });
          };
          poll();
        }, 5000);
        const jobId = env.createJobId(data => {
          isConnectedToConcurrentCapableServer = true;
          console.log('handleUpdate:', data);
          if (data.status == 'done') {
            isDone = true;
            resolve(data.result);
            localConcurrentCleanup();
            return;
          }
          // Status update, ignore
          console.log('ignoring status update?', data);
          if (statusPre) {
            const lines = [];
            lines.push(`Property evaluation is taking a while, status below:`);
            lines.push(`Status: ${data.status}`);
            if (data.stack) {
              lines.push("Stack trace:");
              data.stack.forEach((ste: string) => lines.push(`> ${ste}`));
            }
            statusPre.innerText = lines.join('\n');
          }
        });
        activelyLoadingJob = jobId;
        env.performRpcQuery({
          attr,
          locator,
        }, { jobId })
          .then(data => {
            if (data.job) {
              // Async work queued, not done.
            } else {
              // Sync work executed, done.
              isDone = true;
              resolve(data);
            }
          })
          .catch(err => {
            isDone = true;
            reject(err);
          });
      });
      doFetch()
        .then((parsed: RpcResponse | 'stopped') => {
          loading = false;
          if (parsed === 'stopped') {
            refreshOnDone = false;
          }
          if (refreshOnDone) {
            refreshOnDone = false;
            queryWindow.refresh();
          }
          if (cancelToken.cancelled) { return; }
          if (parsed === 'stopped') {
            while (root.firstChild) root.removeChild(root.firstChild);
            root.append(createTitle().element);
            const p = document.createElement('p');
            p.innerText = `Property evaluation stopped. If any update happens (e.g text or setting changed within CodeProber), evaluation will be attempted again.`;
            root.appendChild(p);
          } else {
            const body = parsed.body;
            copyBody = body;
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
            root.appendChild(encodeRpcBodyLines(env, body));
          }
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
