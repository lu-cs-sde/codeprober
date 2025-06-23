import { adjustLocatorAndProperty } from '../../model/adjustLocator';
import { findAllLocatorsWithinNestingPath } from '../../model/findLocatorWithNestingPath';
import ModalEnv, { JobId } from '../../model/ModalEnv';
import { createMutableLocator } from '../../model/UpdatableNodeLocator';
import { NestedWindows, WindowStateDataProbe } from '../../model/WindowState';
import evaluateProperty from '../../network/evaluateProperty';
import { Diagnostic, NodeLocator, Property, RpcBodyLine, StopJobReq, StopJobRes } from '../../protocol';
import displayProbeModal, { prettyPrintProbePropertyName, searchProbePropertyName } from '../popup/displayProbeModal';
import { formatAttrBaseName } from '../popup/formatAttr';
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
  let showDiagnostics = optionalArgs.showDiagnostics ?? true;
  env.probeMarkers[queryId] = showDiagnostics ? localErrors : [];
  let activelyLoadingJobCleanup: (() => void) | null = null;
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
    if (loading && activelyLoadingJobCleanup) {
      activelyLoadingJobCleanup();
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
      loading = true;
      (async () => {
        const src = env.createParsingRequestData();
          const rootEvalProp = evaluateProperty(env, {
            captureStdout: false,
            locator,
            property,
            src,
            type: 'EvaluateProperty',
          },
            // Status update stuff, can we use this here? :thinking:
            () => {},
            () => {},
            () => {},
          );
          const cleanups: (() => void)[] = [];
          cleanups.push(rootEvalProp.cancel);
          activelyLoadingJobCleanup = () => {
            cleanups.forEach(cl => cl());
          };
          const resp = await rootEvalProp.fetch();
        // };

        if (resp === 'stopped') {
          console.warn('evaluateProperty automatically stopped?');
          return;
        }

        const newErrors: Diagnostic[] = [];
        newErrors.push(...resp.errors ?? []);

        const handleLines = async (relatedProp: Property, lines: RpcBodyLine[], nests: NestedWindows) => {
          const nestedRequests: (() => Promise<void>)[] = [];
          Object.entries(findAllLocatorsWithinNestingPath(lines)).forEach(
            ([unprefixedPath, nestedLocator]) => {
              const nwPathKey = JSON.stringify(unprefixedPath);
              const handleNested = async (nwData: WindowStateDataProbe) => {
                const nestedEvalProp = evaluateProperty(env, {
                  captureStdout: false,
                  // No need to capture tracing information in minimized probes
                  locator: nestedLocator,
                  property: nwData.property,
                  src,
                  type: 'EvaluateProperty',
                },
                // Status update stuff, can we use this here? :thinking:
                  () => {},
                  () => {},
                  () => {},
                );
                cleanups.push(nestedEvalProp.cancel);
                const resp = await nestedEvalProp.fetch();
                if (resp === 'stopped') {
                  console.warn('evaluateProperty automatically stopped?');
                  return;
                }
                newErrors.push(...resp.errors ?? []);
                await handleLines(nwData.property, resp.body, nwData.nested);
              }
              nests[nwPathKey]?.forEach(nw => {
                nestedRequests.push(async () => {
                  if (nw.data.type === 'probe') {
                    console.log('mini handle nested 1');
                    await handleNested(nw.data);
                  }
                });
              });
              if (!nests[nwPathKey]?.length && relatedProp.name === searchProbePropertyName) {
                const propName = `${relatedProp.args?.[0]?.value}`;
                if (propName !== '') {
                  nestedRequests.push(() => handleNested({
                    type: 'probe',
                    locator: nestedLocator,
                    property: { name: propName },
                    nested: {}
                  }));
                }
              }
            }
          );
          await Promise.all(nestedRequests.map(nr => nr()));
        };
        await handleLines(property, resp.body, nestedWindows);
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
      })()
        .catch((err) => {
          console.warn('Error when refreshing minimized probe', err);
        })
        .finally(() => {
          loading = false;
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
  const fixedPropName = property.name == searchProbePropertyName
    ? `*.${property.args?.[0]?.value}`
    : (
      property.name == prettyPrintProbePropertyName
        ? '->PrettyPrint'
        : formatAttrBaseName(property.name)
    )
    ;

  if (locator.steps.length === 0 && property.name == searchProbePropertyName) {
    // Hide title?
    typeLbl.style.display = 'none';
    attrLbl.innerText = fixedPropName;
  } else {
    attrLbl.innerText = `.${fixedPropName}`;
  }
  attrLbl.classList.add('syntax-attr');
  clickableUi.appendChild(attrLbl);

  if (property.name == searchProbePropertyName && (property.args?.length ?? 0) >= 2) {
    const pred = document.createElement('span');
    pred.classList.add('syntax-int');
    pred.innerText = `[${property.args?.[1]?.value}]`;
    clickableUi.appendChild(pred);
  }


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
          showDiagnostics,
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
  if (property.name === searchProbePropertyName) {
    const query = property.args?.[0]?.value;
    let tail = '';
    if ((property.args?.length ?? 0) >= 2) {
      tail = `[${property.args?.[1]?.value}]`;
    }
    if (locator.steps.length === 0) {
      return `*.${query}${tail}`;
    }
    return `${source}.*.${query}${tail}`;
  }

  return `${source}.${property.name}`;
}

export { createDiagnosticSource };
export default createMinimizedProbeModal;
