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

window.clearUserSettings = () => {
  settings.set({});
  location.reload();
}

const uiElements = new UIElements();

const doMain = (wsPort: number) => {
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

    const probeWindowStateSavers: { [key: string]: (target: ProbeWindowState[]) => void } = {};
    const triggerWindowSave = () => {
      const states: ProbeWindowState[] = [];
      Object.values(probeWindowStateSavers).forEach(v => v(states));
      settings.setProbeWindowStates(states);
    };

    const notifyLocalChangeListeners = (adjusters?: LocationAdjuster[]) => {
      // Short timeout to easier see changes happening. Remove in prod
      // setTimeout(() => {
        Object.values(onChangeListeners).forEach(l => l(adjusters));
        triggerWindowSave();
      // }, 500);
    }

    function initEditor(editorType: string) {
      if (!location.search) {
        location.search = "editor=" + editorType;
        return;
      }
      document.body.setAttribute('data-theme-light', `${settings.isLightTheme()}`);

    const wsHandler = ((): WebsocketHandler => {
      if (location.search.includes('wsOverHttp=true')) {
        return createWebsocketOverHttpHandler();
      }
      return createWebsocketHandler(
        new WebSocket(`ws://${location.hostname}:${wsPort}`),
        addConnectionCloseNotice
      );
    })();

    const rootElem = document.getElementById('root') as HTMLElement;
    wsHandler.on('init', ({ version: { clean, hash, buildTimeSeconds } }) => {
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

      const defineThemeToggler = (cb: (lightTheme: boolean) => void) => {
        darkModeCheckbox.oninput = (e) => {
          let lightTheme = !darkModeCheckbox.checked;
          settings.setLightTheme(lightTheme);
          document.body.setAttribute('data-theme-light', `${lightTheme}`);
          cb(lightTheme);
        }
        cb(settings.isLightTheme());
      };

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
            defineThemeToggler(res.themeToggler);
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

      const modalEnv: ModalEnv = {
        performRpcQuery: (args) => performRpcQuery(wsHandler, args),
        probeMarkers, onChangeListeners, updateMarkers,
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
            displayProbeModal(modalEnv, state.modalPos, state.locator, state.attr);
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
      notifyLocalChangeListeners();
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
