import addConnectionCloseNotice from "./ui/addConnectionCloseNotice";
import displayProbeModal from "./ui/popup/displayProbeModal";
import displayRagModal from "./ui/popup/displayRagModal";
import displayHelp from "./ui/popup/displayHelp";
import displayAttributeModal from "./ui/popup/displayAttributeModal";
import settings from './settings';
import StatisticsCollectorImpl from "./model/StatisticsCollectorImpl";
import displayStatistics from "./ui/popup/displayStatistics";
import displayMainArgsOverrideModal from "./ui/popup/displayMainArgsOverrideModal";
import { getAvailableLanguages } from "./model/syntaxHighlighting";
import createWebsocketHandler, { WebsocketHandler, createWebsocketOverHttpHandler } from "./createWebsocketHandler";
import configureCheckboxWithHiddenButton from "./ui/configureCheckboxWithHiddenButton";
import UIElements from "./ui/UIElements";
import showVersionInfo from "./ui/showVersionInfo";
import { TextSpanStyle } from "./ui/create/createTextSpanIndicator";
import runBgProbe from "./model/runBgProbe";
import createCullingTaskSubmitterFactory from "./model/cullingTaskSubmitterFactory";
import displayAstModal from "./ui/popup/displayAstModal";
import { createTestManager } from './model/test/TestManager';
import displayTestSuiteListModal from './ui/popup/displayTestSuiteListModal';
import ModalEnv from './model/ModalEnv';


window.clearUserSettings = () => {
  settings.set({});
  location.reload();
}

const uiElements = new UIElements();

const doMain = (wsPort: number | 'ws-over-http' | { type: 'codespaces-compat', 'from': number, to: number }) => {
  if (settings.shouldHideSettingsPanel() && !window.location.search.includes('fullscreen=true')) {
    document.body.classList.add('hide-settings');
  }
  if (!settings.shouldEnableTesting()) {
    uiElements.showTests.style.display = 'none';
  }
  let getLocalState = () => settings.getEditorContents() ?? '';
  let basicHighlight: Span | null = null;
  const stickyHighlights: { [probeId: string]: StickyHighlight } = {};
  let updateSpanHighlight = (span: Span | null, stickies: StickyHighlight[]) => {};
  const performRpcQuery = (handler: WebsocketHandler, props: { [key: string]: any }) => handler.sendRpc({
    posRecovery: uiElements.positionRecoverySelector.value,
    cache: uiElements.astCacheStrategySelector.value,
    type: 'query',
    text: getLocalState(),
    stdout: settings.shouldCaptureStdio(),
    query: props,
    mainArgs: settings.getMainArgsOverride(),
    tmpSuffix: settings.getCurrentFileSuffix(),
  });

    const onChangeListeners: ModalEnv['onChangeListeners'] = {};

    const probeWindowStateSavers: { [key: string]: (target: WindowState[]) => void } = {};
    const triggerWindowSave = () => {
      const states: WindowState[] = [];
      Object.values(probeWindowStateSavers).forEach(v => v(states));
      settings.setProbeWindowStates(states);
    };

    const notifyLocalChangeListeners = (adjusters?: LocationAdjuster[], reason?: string) => {
      Object.values(onChangeListeners).forEach(l => l(adjusters, reason));
      triggerWindowSave();
    }

    function initEditor(editorType: string) {
      if (!location.search) {
        location.search = "editor=" + editorType;
        return;
      }
      document.body.setAttribute('data-theme-light', `${settings.isLightTheme()}`);

    const wsHandler = ((): WebsocketHandler => {
      if (wsPort == 'ws-over-http') {
        return createWebsocketOverHttpHandler(addConnectionCloseNotice);
      }
      if (typeof wsPort == 'object') {
        // Codespaces-compat
        const needle = `-${wsPort.from}.`;
        if (location.hostname.includes(needle) && !location.port) {
          return createWebsocketHandler(
            new WebSocket(`wss://${location.hostname.replace(needle, `-${wsPort.to}.`)}`),
            addConnectionCloseNotice
          );
        } else {
          // Else, we are running Codespaces locally from a 'native' (non-web) editor.
          // We only need to do the compat layer if running Codespaces from the web.
          // Fall down to default impl below.
        }
      }
      return createWebsocketHandler(
        new WebSocket(`ws://${location.hostname}:${wsPort}`),
        addConnectionCloseNotice
      );
    })();

    const rootElem = document.getElementById('root') as HTMLElement;
    wsHandler.on('init', ({ version: { clean, hash, buildTimeSeconds }, changeBufferTime }) => {
      console.log('onInit, buffer:', changeBufferTime);
      rootElem.style.display = "grid";

      const onChange = (newValue: string, adjusters?: LocationAdjuster[]) => {
        settings.setEditorContents(newValue);
        notifyLocalChangeListeners(adjusters);
      };

      let setLocalState = (value: string) => { };
      let markText: TextMarkFn = () => ({});
      let registerStickyMarker: (initialSpan: Span) => StickyMarker = (initialSpan) => ({
        getSpan: () => initialSpan,
        remove: () => {},
      });

      const darkModeCheckbox  = uiElements.darkModeCheckbox;
      darkModeCheckbox.checked = !settings.isLightTheme();
      const themeChangeListeners: { [id: string]: (lightTheme: boolean) => void } = {};
      darkModeCheckbox.oninput = (e) => {
        let lightTheme = !darkModeCheckbox.checked;
        settings.setLightTheme(lightTheme);
        document.body.setAttribute('data-theme-light', `${lightTheme}`);
        Object.values(themeChangeListeners).forEach(cb => cb(lightTheme));
      }

      let syntaxHighlightingToggler: ((langId: SyntaxHighlightingLanguageId) => void) | undefined;

      if (window.definedEditors[editorType]) {
        const { preload, init, } = window.definedEditors[editorType];
        window.loadPreload(preload, () => {
          const res = init(settings.getEditorContents() ?? `// Hello World!\n// Write some code in this field, then right click and select 'Create Probe' to get started\n\n`, onChange, settings.getSyntaxHighlighting());
          setLocalState = res.setLocalState || setLocalState;
          getLocalState = res.getLocalState || getLocalState;
          updateSpanHighlight = res.updateSpanHighlight || updateSpanHighlight;
          registerStickyMarker = res.registerStickyMarker || registerStickyMarker;
          markText = res.markText || markText;
          if (res.themeToggler) {
            themeChangeListeners['main-editor'] = (light) => res.themeToggler(light);
            res.themeToggler(settings.isLightTheme());
            // defineThemeToggler(res.themeToggler);
          }
          syntaxHighlightingToggler = res.syntaxHighlightingToggler;

          location.search.split(/\?|&/g).forEach((kv) => {
            const needle = `bgProbe=`;
            if (kv.startsWith(needle)) {
              runBgProbe(
                modalEnv,
                { result: { start: 0, end: 0, type: '<ROOT>' }, steps: [] },
                { name: kv.slice(needle.length), },
              );
            }
          });
        })

      } else {
        // Throw out to editor selection screen
        location.search = '';
      }

      const activeMarkers: TextMarker[] = [];
      const probeMarkers: ModalEnv['probeMarkers'] = {};
      const updateMarkers = () => {
        activeMarkers.forEach(m => m?.clear?.());
        activeMarkers.length = 0;

        const deduplicator = new Set();
        const filteredAddMarker = (severity: MarkerSeverity, start: number, end: number, msg: string) => {
          const uniqId = [severity, start, end, msg].join(' | ');;
          if (deduplicator.has(uniqId)) {
            return;
          }
          deduplicator.add(uniqId);

          const lineStart = (start >>> 12);
          const colStart = start & 0xFFF;
          const lineEnd = (end >>> 12);
          const colEnd = end & 0xFFF;
          activeMarkers.push(markText({ severity, lineStart, colStart, lineEnd, colEnd, message: msg }));
        }
        Object.values(probeMarkers).forEach(arr => arr.forEach(({ severity, errStart, errEnd, msg }) => filteredAddMarker(severity, errStart, errEnd, msg)));
      };

      const setupSimpleCheckbox = (input: HTMLInputElement, initial: boolean, update: (checked: boolean) => void) => {
        input.checked = initial;
        input.oninput = () => { update(input.checked); notifyLocalChangeListeners(); }
      };
      setupSimpleCheckbox(uiElements.captureStdoutCheckbox, settings.shouldCaptureStdio(), cb => settings.setShouldCaptureStdio(cb));
      setupSimpleCheckbox(uiElements.duplicateProbeCheckbox, settings.shouldDuplicateProbeOnAttrClick(), cb => settings.setShouldDuplicateProbeOnAttrClick(cb));
      setupSimpleCheckbox(uiElements.showAllPropertiesCheckbox, settings.shouldShowAllProperties(), cb => settings.setShouldShowAllProperties(cb));

      const setupSimpleSelector = (input: HTMLSelectElement, initial: string, update: (val: string) => void) => {
        input.value = initial;
        input.oninput = () => { update(input.value); notifyLocalChangeListeners(); };
      };
      setupSimpleSelector(uiElements.astCacheStrategySelector, settings.getAstCacheStrategy(), cb => settings.setAstCacheStrategy(cb));
      setupSimpleSelector(uiElements.positionRecoverySelector, settings.getPositionRecoveryStrategy(), cb => settings.setPositionRecoveryStrategy(cb));
      setupSimpleSelector(uiElements.locationStyleSelector, `${settings.getLocationStyle()}`, cb => settings.setLocationStyle(cb as TextSpanStyle));

      uiElements.settingsHider.onclick = () => {
        document.body.classList.add('hide-settings');
        settings.setShouldHideSettingsPanel(true);
      };
      uiElements.settingsRevealer.onclick = () => {
        document.body.classList.remove('hide-settings');
        settings.setShouldHideSettingsPanel(false);
      };

      const syntaxHighlightingSelector = uiElements.syntaxHighlightingSelector;
      syntaxHighlightingSelector.innerHTML = '';
      getAvailableLanguages().forEach(({ id, alias }) => {
        const option = document.createElement('option');
        option.value = id;
        option.innerText = alias;
        syntaxHighlightingSelector.appendChild(option);
      })
      setupSimpleSelector(syntaxHighlightingSelector, settings.getSyntaxHighlighting(), cb => {
        settings.setSyntaxHighlighting(syntaxHighlightingSelector.value as SyntaxHighlightingLanguageId);
        syntaxHighlightingToggler?.(settings.getSyntaxHighlighting());
      });

      const overrideCfg = configureCheckboxWithHiddenButton(uiElements.shouldOverrideMainArgsCheckbox, uiElements.configureMainArgsOverrideButton,
        (checked) => { // On checkbox update
          settings.setMainArgsOverride(checked ? [] : null);
          overrideCfg.refreshButton();
          notifyLocalChangeListeners();
        },
        onClose => displayMainArgsOverrideModal(onClose, () => {
          overrideCfg.refreshButton();
          notifyLocalChangeListeners()
        }),
        () => { // Get styling
          const overrides = settings.getMainArgsOverride();
          return overrides === null ? null : `Edit (${overrides.length})`
        },
      );

      const suffixCfg = configureCheckboxWithHiddenButton(uiElements.shouldCustomizeFileSuffixCheckbox, uiElements.configureCustomFileSuffixButton,
        (checked) => { // On checkbox update
          settings.setCustomFileSuffix(checked ? settings.getCurrentFileSuffix() : null);
          suffixCfg.refreshButton();
          notifyLocalChangeListeners();
        },
        onClose => {
          const newVal = prompt('Enter new suffix', settings.getCurrentFileSuffix());
          if (newVal !== null) {
            settings.setCustomFileSuffix(newVal);
              suffixCfg.refreshButton();
              notifyLocalChangeListeners()
          }
          onClose();
          return { forceClose: () => { }, };
        },
        () => { // Get styling
          const overrides = settings.getCustomFileSuffix();
          return overrides === null ? null : `Edit (${settings.getCurrentFileSuffix()})`;
        },
      );

      const statCollectorImpl = new StatisticsCollectorImpl();
      if (location.search.includes('debug=true')) {
        document.getElementById('secret-debug-panel')!.style.display = 'block';
      }

      // const testManager = createTestManager(req => performRpcQuery(wsHandler, req));
      const testManager = createTestManager(req => wsHandler.sendRpc(req));
      const modalEnv: ModalEnv = {
        performRpcQuery: (args) => performRpcQuery(wsHandler, args),
        probeMarkers, onChangeListeners, themeChangeListeners, updateMarkers,
        themeIsLight: () => settings.isLightTheme(),
        getLocalState: () => getLocalState(),
        captureStdout: () => uiElements.captureStdoutCheckbox.checked,
        duplicateOnAttr: () => uiElements.duplicateProbeCheckbox.checked,
        registerStickyMarker: (...args) => registerStickyMarker(...args),
        updateSpanHighlight: (hl) => {
          basicHighlight = hl;
          updateSpanHighlight(basicHighlight, Object.values(stickyHighlights));
        },
        probeWindowStateSavers,
        setStickyHighlight: (pi, hl) => {
          stickyHighlights[pi] = hl;
          updateSpanHighlight(basicHighlight, Object.values(stickyHighlights));
        },
        clearStickyHighlight: (pi) => {
          delete stickyHighlights[pi];
          updateSpanHighlight(basicHighlight, Object.values(stickyHighlights));
        },
        triggerWindowSave,
        statisticsCollector: statCollectorImpl,
        currentlyLoadingModals: new Set<string>(),
        createCullingTaskSubmitter: createCullingTaskSubmitterFactory(changeBufferTime),
        testManager,
       };

       modalEnv.onChangeListeners['reeval-tests-on-server-refresh'] = (_, reason) => {
        if (reason === 'refresh-from-server') {
          testManager.flushTestCaseData();
        }
       };

      uiElements.showTests.onclick = () => {
        uiElements.showTests.disabled = true;
        displayTestSuiteListModal(
          modalEnv,
          () => { uiElements.showTests.disabled = false; },
        );
      };

       showVersionInfo(uiElements.versionInfo, hash, clean, buildTimeSeconds, wsHandler);

      window.displayHelp = (type) => {
        const common = (type: HelpType, button: HTMLButtonElement) => displayHelp(type, disabled => button.disabled = disabled);
        switch (type) {
          case "general": return common('general', uiElements.generalHelpButton);
          case 'recovery-strategy': return common('recovery-strategy', uiElements.positionRecoveryHelpButton);
          case "ast-cache-strategy": return common('ast-cache-strategy', uiElements.astCacheStrategyHelpButton);
          case "probe-statistics": return displayStatistics(
            statCollectorImpl,
            disabled => uiElements.displayStatisticsButton.disabled = disabled,
            newContents => setLocalState(newContents),
            () => modalEnv.currentlyLoadingModals.size > 0,
          );
          case 'syntax-highlighting': return common('syntax-highlighting', uiElements.syntaxHighlightingHelpButton);
          case 'main-args-override': return common('main-args-override', uiElements.mainArgsOverrideHelpButton);
          case 'customize-file-suffix':  return common('customize-file-suffix', uiElements.customFileSuffixHelpButton);
          case 'show-all-properties': return common('show-all-properties', uiElements.showAllPropertiesHelpButton)
          case 'duplicate-probe-on-attr': return common('duplicate-probe-on-attr', uiElements.duplicateProbeHelpButton)
          case 'capture-stdout': return common('capture-stdout', uiElements.captureStdoutHelpButton);
          case 'location-style': return common('location-style', uiElements.locationStyleHelpButton);
          default: return console.error('Unknown help type', type);
        }
      }
      setTimeout(() => {
        try {
          settings.getProbeWindowStates().forEach((state) => {
            switch (state.data.type) {
              case 'probe': {
                displayProbeModal(modalEnv, state.modalPos, state.data.locator, state.data.attr);
                break;
              }
              case 'ast': {
                displayAstModal(modalEnv, state.modalPos, state.data.locator, state.data.direction, state.data.transform);
                break;
              }
              default: {
                console.warn('Unexpected probe window state type:', state.data);
              }
            }
          });
        }  catch (e) {
          console.warn('Invalid probe window state?', e);
        }
      }, 300); // JUUUUUUUST in case the stored window state causes issues, this 300ms timeout allows people to click the 'clear state' button
      window.RagQuery = (line, col, autoSelectRoot) => {
        if (autoSelectRoot) {
          const node: TypeAtLocStep = { type: '<ROOT>', start: (line << 12) + col - 1, end: (line << 12) + col + 1, depth: 0 };
          displayAttributeModal(modalEnv, null, { result: node, steps: [] });
        } else {
          displayRagModal(modalEnv, line, col);

        }
      }
    });
    wsHandler.on('refresh', () => {
      notifyLocalChangeListeners(undefined, 'refresh-from-server');
    });
  }

  initEditor('Monaco');
}

window.initCodeProber = () => {
  (async () => {
    const socketRes = await fetch('/WS_PORT');
    if (socketRes.status !== 200) {
      throw new Error(`Unexpected status code when fetch websocket port ${socketRes.status}`);
    }
    const txt = await socketRes.text();
    if (txt === 'http') {
      return doMain('ws-over-http');
    }
    if (txt.startsWith('codespaces-compat:')) {
      const parts = txt.slice('codespaces-compat:'.length).split(':');
      if (parts.length === 2) {
        const from = Number.parseInt(parts[0], 10);
        const to = Number.parseInt(parts[1], 10);
        if (Number.isNaN(from) || Number.isNaN(to)) {
          throw new Error(`Bad codespaces compat values: [${from},${to}]`);
        }
        return doMain({ type: 'codespaces-compat', from, to });
      } else {
        throw new Error(`Bad codespaces compat values: ${parts.join(", ")}`);
      }
    }
    const port = Number.parseInt(txt, 10);
    if (Number.isNaN(port)) {
      throw new Error(`Bad websocket response text ${txt}`);
    }
    return doMain(port);
  })().catch(err => {
    console.warn('Failed fetching websocket port, falling back to 8080', err);
    doMain(8080);
  })
}
