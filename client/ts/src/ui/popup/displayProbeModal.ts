import createLoadingSpinner from "../create/createLoadingSpinner";
import createModalTitle from "../create/createModalTitle";
import { adjustValue } from "../../model/adjustLocator";
import displayHelp from "./displayHelp";
import encodeRpcBodyLines from "./encodeRpcBodyLines";
import createStickyHighlightController from '../create/createStickyHighlightController';
import ModalEnv, { JobId } from '../../model/ModalEnv';
import displayTestAdditionModal from './displayTestAdditionModal';
import renderProbeModalTitleLeft from '../renderProbeModalTitleLeft';
import settings from '../../settings';
import { Property, EvaluatePropertyReq, EvaluatePropertyRes, RpcBodyLine, StopJobReq, StopJobRes, SynchronousEvaluationResult, PollWorkerStatusReq, PollWorkerStatusRes } from '../../protocol';
import displayAttributeModal from './displayAttributeModal';
import displayAstModal from './displayAstModal';
import createInlineWindowManager, { InlineWindowManager } from '../create/createInlineWindowManager';
import UpdatableNodeLocator, { createImmutableLocator, createMutableLocator } from '../../model/UpdatableNodeLocator';
import { NestedWindows, WindowStateDataProbe } from '../../model/WindowState';
import { NestedTestRequest } from '../../model/test/TestManager';
import SourcedDiagnostic from '../../model/SourcedDiagnostic';
import { createDiagnosticSource } from '../create/createMinimizedProbeModal';
import evaluateProperty, { OngoingPropertyEvaluation } from '../../network/evaluateProperty';

const metaNodesWithPropertyName = `m:NodesWithProperty`;

interface OptionalArgs {
  showDiagnostics?: boolean;
}
const displayProbeModal = (
  env: ModalEnv,
  modalPos: ModalPosition | null,
  locator: UpdatableNodeLocator,
  property: Property,
  nestedWindows: NestedWindows,
  optionalArgs: OptionalArgs = {},
) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  const localDiagnostics: SourcedDiagnostic[] = [];
  let diagnosticsGetter: SourcedDiagnostic[] | (() => SourcedDiagnostic[]) = localDiagnostics;
  let showDiagnostics = optionalArgs.showDiagnostics ?? true;
  let updateMarkers = env.updateMarkers;
  const stickyController = createStickyHighlightController(env);
  let activelyLoadingJob: OngoingPropertyEvaluation | null = null;
  let loading = false;
  let isCleanedUp = false;
  nestedWindows = {...nestedWindows}; // Make copy so we can locally modify it

  const reinstallDiagnosticsGetter = () => {
    env.probeMarkers[queryId] = showDiagnostics ? diagnosticsGetter : [];
  };
  reinstallDiagnosticsGetter();

  const doCreateInlineWindowManager = (): {
    inlineWindowManager: InlineWindowManager,
    shouldShowDiagnosticsToggler: boolean,
  } => {
    if (env !== env.getGlobalModalEnv()) {
      return {
        inlineWindowManager: createInlineWindowManager(),
        shouldShowDiagnosticsToggler: false,
      };
    }

    const override: ModalEnv['probeMarkers'] = {};
    diagnosticsGetter = () => {
      const sum = [...localDiagnostics]
      Object.values(override).forEach(val => {
        sum.push(...(Array.isArray(val) ? val : val()).map(diag => {
          // Replace source with ourselves
          return { ...diag, source: createDiagnosticSource(locator.get(), property) }
        }));
      })
      return sum;
    };
    reinstallDiagnosticsGetter();
    return {
      inlineWindowManager: createInlineWindowManager({
        probeMarkersOverride: override,
        updateMarkersOverride: () => updateMarkers(),
      }),
      shouldShowDiagnosticsToggler: true
    }
  }
  const { inlineWindowManager, shouldShowDiagnosticsToggler } = doCreateInlineWindowManager();
  // const inline

  const doStopJob = (jobId: JobId) => env.performTypedRpc<StopJobReq, StopJobRes>({
    type: 'Concurrent:StopJob',
    job: jobId,
  }).then(res => {
    if (res.err) {
      console.warn('Error when stopping job:', res.err);
      return false;
    }
    return true;
  });

  const cleanup = () => {
    queryWindow.remove();
    isCleanedUp = true;
    delete env.onChangeListeners[queryId];
    delete env.probeMarkers[queryId];
    delete env.probeWindowStateSavers[queryId];
    env.currentlyLoadingModals.delete(queryId);
    stickyController.cleanup();
    if (loading && activelyLoadingJob !== null) {
      // doStopJob(activelyLoadingJob);
      activelyLoadingJob.cancel();
    }
    // console.log('cleanup: ', queryId, 'inline state:', inlineWindowManager.getWindowStates());
    inlineWindowManager.destroy();
    env.updateMarkers();
    env.triggerWindowSave();
  };

  let copyBody: RpcBodyLine[] = [];

  const getNestedTestRequests = (path: number[], state: WindowStateDataProbe): NestedTestRequest => {
    const nested: NestedTestRequest[] = [];
    Object.entries(state.nested).forEach(([path, val]) => {
      const pathIndexes: number[] = JSON.parse(path);
      val.forEach(v => {
        if (v.data.type !== 'probe') { return; }
        nested.push(getNestedTestRequests(pathIndexes, v.data));
      });
    });
    return { path, property: state.property, nested, }
  };
  const createTitle = () => {
    const titleNode = createModalTitle({
      extraActions: [
        ...(
          env.getGlobalModalEnv() === env
            ? [
              {
                title: 'Duplicate window',
                invoke: () => {
                  const pos = queryWindow.getPos();
                  displayProbeModal(env,
                    { x: pos.x + 10, y: pos.y + 10 },
                    locator.createMutableClone(), property, inlineWindowManager.getWindowStates(),
                    { showDiagnostics }
                    );
                },
              },
              {
                title: 'Minimize window',
                invoke: () => {
                  env.minimize(getWindowStateData());
                  cleanup();
                }
              }
            ]
            : [{
              title: 'Detatch window',
              invoke: () => {
                const states = inlineWindowManager.getWindowStates();
                cleanup();
                displayProbeModal(env.getGlobalModalEnv(), null, locator.createMutableClone(), property, states, { showDiagnostics });
              },
            }]
        ),
        {
          title: 'Copy input to clipboard',
          invoke: () => {
            navigator.clipboard.writeText(JSON.stringify({ locator: locator.get(), property }, null, 2));
          }
        },
        {
          title: 'Copy output to clipboard',
          invoke: () => {
            navigator.clipboard.writeText(copyBody.map(line => typeof line === 'string' ? line : JSON.stringify(line, null, 2)).join('\n'));
          }
        },
        ...((property.args?.length ?? 0) === 0 ? [
          {
            title: 'Create metaProbe',
            invoke: () => {
              displayProbeModal(env, null, locator, { name: metaNodesWithPropertyName, args: [{ type: 'string', value: property.name }] }, {});
            }
          },
        ] : []),
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
          settings.shouldEnableTesting() && (env === env.getGlobalModalEnv()) && {
            title: 'Save as test',
            invoke: () => {
              const nestedReq = getNestedTestRequests([], getWindowStateData());
              displayTestAdditionModal(env, queryWindow.getPos(), locator.get(), {
                property: nestedReq.property,
                nested: nestedReq.nested,
              });
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
          cleanup();
        },
        () => queryWindow.getPos(),
        stickyController, locator, property, nestedWindows,
        env === env.getGlobalModalEnv() ? 'default' : 'minimal-nested',
      ),
      onClose: () => {
        cleanup();
      },
    }).element;
    if (env === env.getGlobalModalEnv()) {
      titleNode.style.zIndex = `1`;
    }
    return titleNode;
  };
  let lastSpinner: HTMLElement | null = null;
  let isFirstRender = true;
  let refreshOnDone = false;

  const queryWindow = env.showWindow({
    pos: modalPos,
    debugLabel: `probe:${property.name}`,
    rootStyle: `
      min-width: 16rem;
      min-height: fit-content;
    `,
    resizable: true,
    onFinishedMove: () => env.triggerWindowSave(),
    onForceClose: cleanup,
    render: (root, { cancelToken, bringToFront }) => {
      if (lastSpinner != null) {
        lastSpinner.style.display = 'inline-block';
        lastSpinner = null;
      } else if (isFirstRender) {
        isFirstRender = false;
        while (root.firstChild) root.removeChild(root.firstChild);
        root.appendChild(createTitle());
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

      const doFetch = (): Promise<SynchronousEvaluationResult | 'stopped'> => {
        let statusPre: HTMLElement | null = null;
        let stopBtn: HTMLButtonElement | null = null;

        const epFetch = evaluateProperty(env, {
          type: 'EvaluateProperty',
          property,
          locator: locator.get(),
          src: env.createParsingRequestData(),
          captureStdout: settings.shouldCaptureStdio(),

          jobLabel: `Probe: '${`${locator.get().result.label ?? locator.get().result.type}`.split('.').slice(-1)[0]}.${property.name}'`,
          skipResultLocator: env !== env.getGlobalModalEnv(),
        },
          () => { // On slow
            const stopBtn = document.createElement('button');
            stopBtn.innerText = 'Stop';
            stopBtn.onclick = () => {
              epFetch.cancel();
            }
            root.appendChild(stopBtn);

            statusPre = document.createElement('p');
            statusPre.style.whiteSpace = 'pre';
            statusPre.style.fontFamily = 'monospace';
            root.appendChild(statusPre);
            statusPre.innerText = `Request takes a while, polling status..\nIf you see message for longer than a few milliseconds then the job hasn't started running yet, or the server is severely overloaded.`;
          },
          (status, stackTrace) => {
            if (!statusPre) { return; }
            const lines = [];
            lines.push(`Property evaluation is taking a while, status below:`);
            lines.push(`Status: ${status}`);
            if (stackTrace) {
              lines.push("Stack trace:");
              stackTrace.forEach((ste) => lines.push(`> ${ste}`));
            }
            statusPre.innerText = lines.join('\n');
          },
          () => { // Cleanup
            if (stopBtn) { root.removeChild(stopBtn); }
            if (statusPre) { root.removeChild(statusPre); }
          }
        )
        activelyLoadingJob = epFetch;
        return epFetch.fetch();
      }
      // const doFetch = () => new Promise<SynchronousEvaluationResult | 'stopped'>(async (resolve, reject) => {
      //   let isDone = false;
      //   let isConnectedToConcurrentCapableServer = false;
      //   let statusPre: HTMLElement | null = null;
      //   let localConcurrentCleanup = () => {};
      //   const initialPollDelayTimer = setTimeout(() => {
      //     if (isDone || isCleanedUp || !isConnectedToConcurrentCapableServer) {
      //       return;
      //     }
      //     const stop = document.createElement('button');
      //     stop.innerText = 'Stop';
      //     stop.onclick = () => {
      //       doStopJob(jobId).then(stopped => {
      //         if (stopped) {
      //           isDone = true;
      //           resolve('stopped');
      //         }
      //         // Else, job might have finished just as the user clicked stop
      //       });
      //     }
      //     root.appendChild(stop);

      //     statusPre = document.createElement('p');
      //     statusPre.style.whiteSpace = 'pre';
      //     statusPre.style.fontFamily = 'monospace';
      //     root.appendChild(statusPre);
      //     statusPre.innerText = `Request takes a while, polling status..\nIf you see message for longer than a few milliseconds then the job hasn't started running yet, or the server is severely overloaded.`;

      //     localConcurrentCleanup = () => {
      //       root.removeChild(stop);
      //       if (statusPre) { root.removeChild(statusPre); }
      //     };
      //     const poll = () => {
      //       if (isDone || isCleanedUp) {
      //         return;
      //       }
      //       env.performTypedRpc<PollWorkerStatusReq, PollWorkerStatusRes>({
      //         type: 'Concurrent:PollWorkerStatus',
      //         job: jobId,
      //       })
      //         .then(res => {
      //           if (res.ok) {
      //             // Polled OK, async update will be delivered to job monitor below
      //             // Queue future poll.
      //             setTimeout(poll, 1000);
      //           } else {
      //             console.warn('Error when polling for job status');
      //             // Don't queue more polling, very unlikely to work anyway.
      //           }
      //         });
      //     };
      //     poll();
      //   }, 5000);
      //   const jobId = env.createJobId(data => {
      //     isConnectedToConcurrentCapableServer = true;
      //     let knownStatus = 'Unknown';
      //     let knownStackTrace: string[] | null = null;
      //     const refreshStatusPre = () => {
      //       if (!statusPre) { return; }
      //       const lines = [];
      //       lines.push(`Property evaluation is taking a while, status below:`);
      //       lines.push(`Status: ${knownStackTrace}`);
      //       if (knownStackTrace) {
      //         lines.push("Stack trace:");
      //         knownStackTrace.forEach((ste) => lines.push(`> ${ste}`));
      //       }
      //       statusPre.innerText = lines.join('\n');
      //     }
      //     switch (data.value.type) {

      //       case 'status': {
      //         knownStatus = data.value.value;
      //         refreshStatusPre();
      //         break;
      //       }
      //       case 'workerStackTrace': {
      //         knownStackTrace = data.value.value;
      //         refreshStatusPre();
      //         break;
      //       }

      //       case 'workerTaskDone': {
      //         const res = data.value.value;
      //         isDone = true;
      //         localConcurrentCleanup();
      //         if (res.type === 'normal') {
      //           const cast = res.value as EvaluatePropertyRes;
      //           if (cast.response.type == 'job') {
      //             throw new Error(`Unexpected 'job' result in async update`);
      //           }
      //           resolve(cast.response.value);
      //         } else {
      //           console.log('Worker task failed. This is likely an internal CodeProber issue. Error message:');
      //           res.value.forEach(line => console.log(line));
      //           reject('Worker failed');
      //         }
      //         break;
      //       }

      //       default: {
      //         // Ignore
      //       }
      //     }
      //   });
      //   env.performTypedRpc<EvaluatePropertyReq, EvaluatePropertyRes>({
      //     type: 'EvaluateProperty',
      //     property,
      //     locator: locator.get(),
      //     src: env.createParsingRequestData(),
      //     captureStdout: settings.shouldCaptureStdio(),
      //     job: jobId,

      //     jobLabel: `Probe: '${`${locator.get().result.label ?? locator.get().result.type}`.split('.').slice(-1)[0]}.${property.name}'`,
      //     skipResultLocator: env !== env.getGlobalModalEnv(),
      //   })
      //     .then(data => {
      //       if (data.response.type === 'job') {
      //         // Async work queued, not done.
      //         if (isCleanedUp) {
      //           // We were removed while this request was sent
      //           // Stop it asap
      //           doStopJob(jobId);
      //         } else {
      //           activelyLoadingJob = jobId;
      //         }
      //         isConnectedToConcurrentCapableServer = true;
      //       } else {
      //         // Sync work executed, done.
      //         clearTimeout(initialPollDelayTimer);
      //         isDone = true;
      //         resolve(data.response.value);
      //       }
      //     })
      //     .catch(err => {
      //       isDone = true;
      //       reject(err);
      //     });
      // });
      doFetch()
        .then((parsed) => {
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
            root.append(createTitle());
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

            let shouldRefreshMarkers = localDiagnostics.length > 0;
            localDiagnostics.length = 0;

            localDiagnostics.push(...(parsed.errors ?? []).map((err): SourcedDiagnostic => {
              return ({ ...err, source: createDiagnosticSource(locator.get(), property) });
            }));
            // parsed.errors?.forEach(({severity, start: errStart, end: errEnd, msg }) => {
            //   localErrors.push({ severity, errStart, errEnd, msg });
            // })
            const updatedArgs = parsed.args;
            if (updatedArgs) {
              shouldRefreshMarkers = true;
              property.args?.forEach((arg, argIdx) => {
                arg.type = updatedArgs[argIdx].type;
                // arg.detail = updatedArgs[argIdx].detail;
                arg.value = updatedArgs[argIdx].value;
              })
            }
            if (parsed.locator) {
              shouldRefreshMarkers = true;
              locator.set(parsed.locator);
              // locator = parsed.locator;
            }
            if (shouldRefreshMarkers || localDiagnostics.length > 0) {
              updateMarkers();
            }
            const titleRow = createTitle();
            root.append(titleRow);

            const enableExpander = true;
            // const enableExpander = body.length >= 1 && (body[0].type === 'node' || (
            //   body[0].type === 'arr' && body[0].value.length >= 1 && body[0].value[0].type === 'node'
            // ));
            if (property.name === metaNodesWithPropertyName && body.length === 1 && body[0].type === 'arr' && body[0].value.length === 0) {
              const message = document.createElement('div');
              message.style.padding = '0.25rem';
              let tail = '';
              if ((property.args?.length ?? 0) >= 2) {
                tail = ` that match the predicate${`${property.args?.[1]?.value}`.includes(',') ? 's' : ''}`;
              }
              message.innerText = `Found no nodes implementing '${property.args?.[0]?.value}'${tail}`;
              message.style.fontStyle = 'italic';
              root.appendChild(message);
            }

            const areasToKeep = new Set<string>();
            const encodedLines = encodeRpcBodyLines(env, body, {
              excludeStdIoFromPaths: true,
              nodeLocatorExpanderHandler: enableExpander ? ({
                getReusableExpansionArea: (path) => {
                  return inlineWindowManager.getPreviousExpansionArea(path);
                },
                onCreate: ({ locator, locatorRoot, expansionArea, path: nestId, isFresh }) => {
                  areasToKeep.add(JSON.stringify(nestId));
                  const updLocator = inlineWindowManager.getPreviouslyAssociatedLocator(nestId) ?? createMutableLocator(locator);
                  updLocator.set(locator);
                  const area = inlineWindowManager.getArea(nestId, locatorRoot, expansionArea, updLocator, queryWindow.bumpIntoScreen);
                  const nestedEnv = area.getNestedModalEnv(env);

                  const encodedId = JSON.stringify(nestId);
                  const nests = nestedWindows[encodedId];
                  if (nests) {
                    delete nestedWindows[encodedId];
                    const immutLoc = createImmutableLocator(updLocator);
                    nests.forEach(nest => {
                      switch (nest.data.type) {
                        case 'probe': {
                          const dat = nest.data;
                          displayProbeModal(nestedEnv, null, immutLoc, dat.property, dat.nested);
                          break;
                        }
                        case 'ast': {
                          const dat = nest.data;
                          displayAstModal(nestedEnv, null, immutLoc, dat.direction, dat.transform);
                          break;
                        }
                      }
                    });
                  }
                  if (isFresh && property.name === metaNodesWithPropertyName) {
                    const nestedPropName = property.args?.[0]?.value as string;
                    if (!nests?.some((nest) => nest.data.type === 'probe' && nest.data.property.name === nestedPropName)) {
                      displayProbeModal(nestedEnv , null, createImmutableLocator(updLocator), { name: nestedPropName }, {});
                    }
                  }
                },
                onClick: ({ locator, locatorRoot, expansionArea, path: nestId }) => {
                  const prevLocator = inlineWindowManager.getPreviouslyAssociatedLocator(nestId);
                  if (!prevLocator) {
                    console.warn('OnClick on unknown area:', nestId);
                    return;
                  }
                  prevLocator.set(locator);
                  const area = inlineWindowManager.getArea(nestId, locatorRoot, expansionArea, prevLocator, queryWindow.bumpIntoScreen);
                  const nestedEnv = area.getNestedModalEnv(env);
                  displayAttributeModal(nestedEnv, null, createImmutableLocator(prevLocator));
                  env.triggerWindowSave();

                },

              }): undefined,
              // nodeLocatorExpanderHandler: () => {},
            });
            if (shouldShowDiagnosticsToggler) {
              const expl = document.createElement('div');
              expl.style.fontStyle = 'italic';
              expl.style.fontSize = '0.5rem';
              expl.style.flexDirection = 'row';
              expl.style.marginTop = '0.125rem';

              const id = `showdiag-${Math.random()}`;
              const check = createSquigglyCheckbox({
                onInput: (checked) => {
                  showDiagnostics = checked;
                  reinstallDiagnosticsGetter();
                  env.updateMarkers();
                  env.triggerWindowSave();
                },
                initiallyChecked: showDiagnostics,
                id,
              });
              check.classList.add('probeOutputAreaCheckboxWrapper');
              check.style.marginLeft = '0';
              check.style.marginRight = '0';
              expl.appendChild(check);

              const label = document.createElement('label');
              label.style.margin = `auto 0 auto 0.125rem`;
              label.htmlFor = id;
              label.innerText = `Show diagnostics`;
              expl.appendChild(label);

              encodedLines.appendChild(expl);
              const updateExplVisibility = () => {
                if ((Array.isArray(diagnosticsGetter) ? diagnosticsGetter : diagnosticsGetter()).length > 0) {
                  expl.style.display = 'flex';
                } else {
                  expl.style.display = 'none';
                }
              };
              updateExplVisibility();
              updateMarkers = () => {
                updateExplVisibility();
                env.updateMarkers();
              }
              // encodedLines.appendChild(document.createElement('br'));
            };
            root.appendChild(encodedLines);
            inlineWindowManager.conditiionallyDestroyAreas((areaId) => {
              return !areasToKeep.has(JSON.stringify(areaId));
            });
            inlineWindowManager.notifyListenersOfChange();
          }
          const spinner = createLoadingSpinner();
          spinner.style.display = 'none';
          spinner.classList.add('absoluteCenter');
          lastSpinner = spinner;
          root.appendChild(spinner);
          queryWindow.bumpIntoScreen();
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
          console.warn('Failed refreshing probe w/ args:', JSON.stringify({ locator, property }, null, 2));
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
      locator.adjust(adjusters);
      property.args?.forEach((arg) => {
        adjusters.forEach(adj => adjustValue(adj, arg));
      })
    }
    if (loading) {
      refreshOnDone = true;
    } else {
      refresher.submit(() => queryWindow.refresh());
    }
  }

  const getWindowStateData = (): WindowStateDataProbe => {
    return {
      type: 'probe',
      locator: locator.get(),
      property,
      nested: inlineWindowManager.getWindowStates(),
      showDiagnostics: showDiagnostics && undefined, // Only include if necessary to reduce serialized form size
    };
  };
  // if (saveWindowState) {
    env.probeWindowStateSavers[queryId] = (target) => {
      target.push({
        modalPos: queryWindow.getPos(),
        data: getWindowStateData(),
      });
    };
    env.triggerWindowSave();
  // }
}

export { metaNodesWithPropertyName }
export default displayProbeModal;
