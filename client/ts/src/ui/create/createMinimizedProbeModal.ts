import { assertUnreachable } from '../../hacks';
import { adjustLocatorAndProperty } from '../../model/adjustLocator';
import { findAllLocatorsWithinNestingPath } from '../../model/findLocatorWithNestingPath';
import ModalEnv, { JobId } from '../../model/ModalEnv';
import { createMutableLocator } from '../../model/UpdatableNodeLocator';
import { NestedWindows, WindowStateDataProbe } from '../../model/WindowState';
import { Diagnostic, EvaluatePropertyReq, EvaluatePropertyRes, NodeLocator, Property, RpcBodyLine, StopJobReq, StopJobRes } from '../../protocol';
import displayProbeModal, { metaNodesWithPropertyName } from '../popup/displayProbeModal';
import startEndToSpan from '../startEndToSpan';
import registerOnHover from './registerOnHover';

interface OptionalArgs {
  showDiagnostics?: boolean;
}

const createMinimizedProbeModal = (
  env: ModalEnv,
  locator: NodeLocator,
  property: Property,
  nestedWindows: NestedWindows,
  optionalArgs: OptionalArgs = {}
) => {
  const queryId = `minimized-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  const localErrors: Diagnostic[] = [];
  env.probeMarkers[queryId] = localErrors;
  let activelyLoadingJob: JobId | null = null;
  let loading = false;
  let isCleanedUp = false;
  let refreshOnDone = false;

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
    isCleanedUp = true;
    delete env.onChangeListeners[queryId];
    delete env.probeMarkers[queryId];
    delete env.probeWindowStateSavers[queryId];
    env.currentlyLoadingModals.delete(queryId);
    if (loading && activelyLoadingJob !== null) {
      doStopJob(activelyLoadingJob);
    }
    if (localErrors.length > 0) {
      env.updateMarkers();
    }
    env.triggerWindowSave();
  };

  const refresh = () => {
      if (isCleanedUp) {
        return;
      }
      if (loading) {
        refreshOnDone = true;
        return;
      }
      (async () => {
        const src = env.createParsingRequestData();
        const resp = await env.performTypedRpc<EvaluatePropertyReq, EvaluatePropertyRes>({
          captureStdout: false,
          locator,
          property,
          src,
          type: 'EvaluateProperty',
        });
        switch (resp.response.type) {
          case 'job': {
            throw new Error(`Unexpected async response to sync request`);
          }
          case 'sync': {
            const prelen = localErrors.length;
            // localErrors.length = 0;
            const newErrors: Diagnostic[] = [];
            newErrors.push(...resp.response.value.errors ?? []);

            const handleLines = async (relatedProp: Property, lines: RpcBodyLine[], nests: NestedWindows) => {
              const nestedRequests: (() => Promise<void>)[] = [];
              Object.entries(findAllLocatorsWithinNestingPath(lines)).forEach(
                ([unprefixedPath, nestedLocator]) => {
                  // const fixedPath = [...pathPrefix, ...JSON.parse(unprefixedPath)];
                  const nwPathKey = JSON.stringify(unprefixedPath);
                  const handleNested = async (nwData: WindowStateDataProbe) => {
                    const resp = await env.performTypedRpc<EvaluatePropertyReq, EvaluatePropertyRes>({
                      captureStdout: false,
                      locator: nestedLocator,
                      property: nwData.property,
                      src,
                      type: 'EvaluateProperty',
                    });
                    switch (resp.response.type) {
                      case 'job': {
                        throw new Error(`Unexpected async response to sync request`);
                      }
                      case 'sync': {
                        newErrors.push(...resp.response.value.errors ?? []);
                        await handleLines(nwData.property, resp.response.value.body, nwData.nested);
                        break;
                      }
                      default: {
                        assertUnreachable(resp.response);
                        break;
                      }
                    }
                  }
                  nests[nwPathKey]?.forEach(nw => {
                    nestedRequests.push(async () => {
                      if (nw.data.type === 'probe') {
                        console.log('mini handle nested 1');
                        await handleNested(nw.data);
                      }
                    });
                  });
                  if (!nests[nwPathKey]?.length && relatedProp.name === metaNodesWithPropertyName) {
                    console.log('mini handle nested 2');
                    nestedRequests.push(() => handleNested({
                      type: 'probe',
                      locator: nestedLocator,
                      property: { name: `${relatedProp.args?.[0]?.value}` },
                      nested: {}
                    }));
                  }
                }
              );
              await Promise.all(nestedRequests.map(nr => nr()));
            };
            await handleLines(property, resp.response.value.body, nestedWindows);
            if (newErrors.length) {
              squigglyCheckboxWrapper.style.display = 'flex';
            } else {
              squigglyCheckboxWrapper.style.display = 'none';
            }
            if (newErrors.length || localErrors.length) {
              localErrors.length = 0;

              localErrors.push(...newErrors.map(err => ({ ...err, source: createDiagnosticSource(locator, property) })));
              env.updateMarkers();
            }
            break;
          }
          default: {
            assertUnreachable(resp.response);
            break;
          }
        }
      })()
        .catch((err) => {
          console.warn('Error when refreshing minimized probe', err);
        })
        .finally(() => {
          if (refreshOnDone) {
            refreshOnDone = false;
            refresh();
          }
        })

  }
  refresh();

  env.onChangeListeners[queryId] = (adjusters) => {
    if (adjusters) {
      adjusters.forEach(adj => {
        adjustLocatorAndProperty(adj, locator, property);
      })
    }
    refresh();
  };

  const ui = document.createElement('div');
  const clickableUi = document.createElement('div');
  ui.appendChild(clickableUi);

  const typeLbl = document.createElement('span');
  typeLbl.innerText = `${locator.result.label ?? locator.result.type}`.split('.').slice(-1)[0];
  typeLbl.classList.add('syntax-type');
  clickableUi.appendChild(typeLbl);

  const attrLbl = document.createElement('span');
  const fixedPropName = property.name == metaNodesWithPropertyName
    ? `*.${property.args?.[0]?.value}`
    : property.name;

  if (locator.steps.length === 0 && property.name == metaNodesWithPropertyName) {
    // Hide title?
    typeLbl.style.display = 'none';
    attrLbl.innerText = fixedPropName;
  } else {
    attrLbl.innerText = `.${fixedPropName}`;
  }
  attrLbl.classList.add('syntax-attr');
  clickableUi.appendChild(attrLbl);


  env.probeWindowStateSavers[queryId] = (target) => {
    target.push({
      modalPos: { x: 0, y: 0 },
      data: {
        type: 'minimized-probe',
        data: {
          type: 'probe',
          locator,
          property,
          nested: nestedWindows,
        }
      },
    });
  }
  env.triggerWindowSave();

  registerOnHover(
    clickableUi, (on) => env.updateSpanHighlight((on && !locator.result.external) ? startEndToSpan(locator.result.start, locator.result.end) : null),
  )

  clickableUi.onclick = (e) => {
    if (!e.shiftKey && ui.parentElement) {
      ui.parentElement.removeChild(ui);
    }
    env.updateSpanHighlight(null);
    displayProbeModal(
      env,
      null,
      createMutableLocator(locator),
      property,
      nestedWindows,
      { showDiagnostics }
    );
    cleanup();
  }
  let showDiagnostics = optionalArgs.showDiagnostics ?? true;
  const squigglyCheckboxWrapper = createSquigglyCheckbox({
    onInput: (checked) => {
      showDiagnostics = checked;
      env.probeMarkers[queryId] = checked ? localErrors : [];
      env.updateMarkers();
    },
    initiallyChecked: showDiagnostics,
  });
  squigglyCheckboxWrapper.style.marginLeft = '0.125rem';
  squigglyCheckboxWrapper.style.display = 'none';
  ui.appendChild(squigglyCheckboxWrapper);


  ui.classList.add('minimizedProbeWindow');
  clickableUi.classList.add('minimizedProbeWindowOpener');
  squigglyCheckboxWrapper.classList.add('minimiedProbeWindowCheckboxWrapper');

  return { ui };
}

const createDiagnosticSource = (locator: NodeLocator, property: Property) => {
  let source =  `${locator.result.label ?? locator.result.type}`.split('.').slice(-1)[0];
  if (property.name === metaNodesWithPropertyName) {
    const query = property.args?.[0]?.value;
    if (locator.steps.length === 0) {
      return `*.${query}`;
    }
    return `${source}.*.${query}`;
  }

  return `${source}.${property.name}`;
}

export { createDiagnosticSource };
export default createMinimizedProbeModal;
