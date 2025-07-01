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
import createWebsocketHandler, { WebsocketHandler, createWebsocketOverHttpHandler, LocalRequestHandler, createLocalRequestHandler } from "./createWebsocketHandler";
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
import displayWorkerStatus from './ui/popup/displayWorkerStatus';
import { AsyncRpcUpdate, BackingFileUpdated, CompleteReq, CompleteRes, Diagnostic, HoverReq, HoverRes, InitInfo, ParsingRequestData, Refresh, TALStep, WorkspacePathsUpdated } from './protocol';
import showWindow from './ui/create/showWindow';
import { createMutableLocator } from './model/UpdatableNodeLocator';
import WindowState from './model/WindowState';
import { assertUnreachable } from './hacks';
import createMinimizedProbeModal from './ui/create/createMinimizedProbeModal';
import getEditorDefinitionPlace from './model/getEditorDefinitionPlace';
import installASTEditor from './ui/installASTEditor';
import configureCheckboxWithHiddenCheckbox from './ui/configureCheckboxWithHiddenCheckbox';
import Workspace, { initWorkspace } from './model/Workspace';
import TextProbeManager, { setupTextProbeManager, TextProbeStyle } from './model/TextProbeManager';

const uiElements = new UIElements();

window.clearUserSettings = () => {
  settings.set({});
  location.reload();
}

const doMain = (wsPort: number
  | 'ws-over-http'
  | { type: 'codespaces-compat', 'from': number, to: number }
  | { type: 'local-request-handler', handler: LocalRequestHandler }
) => {
  installASTEditor();
  if (settings.shouldHideSettingsPanel() && !window.location.search.includes('fullscreen=true')) {
    document.body.classList.add('hide-settings');
  }
  if (!settings.shouldEnableTesting()) {
    uiElements.showTests.style.display = 'none';
  }
  document.onmousemove = e => {
    document.body.style.setProperty('--mouse-x', (e.pageX + 8) + "px");
    document.body.style.setProperty('--mouse-y', (e.pageY + 8) + "px");

    const isHoveringLeftSideOfScreen = e.pageX < window.innerWidth * 2 / 3;
    // console.log('stuff:', e.screenX, window.outerWidth)
    document.body.style.setProperty('--hover-diag-left', isHoveringLeftSideOfScreen ? ((e.pageX + 8) + 'px') : 'unset');
    document.body.style.setProperty('--hover-diag-right', isHoveringLeftSideOfScreen ? 'unset' : (window.innerWidth - e.pageX + 8) + 'px');

    const isHoveringUpperSideOfScreen = e.pageY < window.innerHeight * 2 / 3;
    document.body.style.setProperty('--hover-diag-top', isHoveringUpperSideOfScreen ? ((e.pageY + 8) + 'px') : 'unset');
    document.body.style.setProperty('--hover-diag-bottom', isHoveringUpperSideOfScreen ? 'unset' : (window.innerHeight - e.pageY + 8) + 'px');
    // repositionTooltips(editor);
  }


  // window.addEventListener("keydown", function (event) {
  //   console.log('keydown:', event.ctrlKey, event.metaKey, ':', event.key)
  //   const platform = this.navigator.platform || '';
  //   const isMacIsh = platform.startsWith("Mac") || platform === "iPhone";
  //   if ((isMacIsh ? event.metaKey : event.ctrlKey) && event.key === "p") {
  //       event.preventDefault();
  //       alert("Printing is disabled on this page.");
  //   }
  // });
  let getLocalState = () => settings.getEditorContents() ?? '';
  let basicHighlight: Span | null = null;
  const stickyHighlights: { [probeId: string]: StickyHighlight } = {};
  let updateSpanHighlight = (span: Span | null, stickies: StickyHighlight[]) => {};
    const onChangeListeners: ModalEnv['onChangeListeners'] = {};

    const probeWindowStateSavers: { [key: string]: (target: WindowState[]) => void } = {};
    const spammyOperationDebouncer = createCullingTaskSubmitterFactory(10);
    const windowSaveDebouncer = spammyOperationDebouncer();
    const getCurrentWindowStates = () => {
      const states: WindowState[] = [];
      Object.values(probeWindowStateSavers).forEach(v => v(states));
      return states;
    };
    let activeWorkspace: Workspace | null = null;
    let activeTextProbeManager: TextProbeManager | null = null;
    const triggerWindowSave = () => {
      windowSaveDebouncer.submit(() => {
        const states = getCurrentWindowStates();
        if (!activeWorkspace || activeWorkspace.activeFileIsTempFile()) {
          settings.setProbeWindowStates(states);
        }
        if (activeWorkspace) {
          activeWorkspace.onActiveWindowsChange(states);
        }
      });
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
        switch (wsPort.type){
          case 'codespaces-compat': {
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
            break;
          }
          case 'local-request-handler': {
            return createLocalRequestHandler(wsPort.handler, addConnectionCloseNotice);
          }
        }
      }
      return createWebsocketHandler(
        new WebSocket(`ws://${location.hostname}:${wsPort}`),
        addConnectionCloseNotice
      );
    })();
    const installWsNotificationHandler = <T extends { type: string }>(s: T['type'], callback: (t: T) => void) => {
      wsHandler.on(s, callback);
    };

    // const foo = <T extends { type: string }>(s: T['type'], callback: (t: T) => void) => {
    const jobUpdateHandlers: {[id: string]: (data: AsyncRpcUpdate) => void }  = {};
    installWsNotificationHandler<AsyncRpcUpdate>('asyncUpdate', (data) => {
      const { job, isFinalUpdate, value } = data;
      if (!job || value === undefined) {
        console.warn('Invalid job update', data);
        return;
      }
      const handler = jobUpdateHandlers[job];
      if (!handler) {
        console.log('Got no handler for job update:', data);
        return;
      }
      if (isFinalUpdate) {
        delete jobUpdateHandlers[data?.job];
      }
      handler(data);
    });

    const loadWindowState = (modalEnv: ModalEnv, state: WindowState) => {
      switch (state.data.type) {
        case 'probe': {
          const data = state.data;
          displayProbeModal(modalEnv,
            state.modalPos,
            createMutableLocator(data.locator),
            data.property,
            data.nested,
            { showDiagnostics: data.showDiagnostics, stickyHighlight: data.stickyHighlight },
          );
          break;
        }
        case 'ast': {
          displayAstModal(modalEnv, state.modalPos, createMutableLocator(state.data.locator), state.data.direction, {
            initialTransform: state.data.transform,
          });
          break;
        }
        case 'minimized-probe': {
          modalEnv.minimize(state.data.data);
          break;
        }
        default: {
          assertUnreachable(state.data);
          break;
        }
      }
    };
    const rootElem = document.getElementById('root') as HTMLElement;
    const initHandler = (info: InitInfo) => {
      const { version: { clean, hash, buildTimeSeconds }, changeBufferTime, workerProcessCount, disableVersionCheckerByDefault, backingFile } = info;
      console.log('onInit, buffer:', changeBufferTime, 'workerProcessCount:', workerProcessCount);

      if ((workerProcessCount ?? 1) <= 1) {
        uiElements.displayWorkerStatusButton.style.display = 'none';
      }
      rootElem.style.display = "grid";

      let shouldTryInitializingWorkspace = false;
      if (backingFile) {
        settings.setEditorContents(backingFile.value);
        const inputLabel = document.querySelector('#input-header > span') as HTMLSpanElement;
        if (!inputLabel) {
          console.warn('Could not find input header')
        } else {
          // inputLabel.innerText = `Input`
          const locIndicator = document.createElement('span');
          locIndicator.style.marginLeft = '0.25rem';
          locIndicator.classList.add('syntax-string');
          locIndicator.innerText = `(${backingFile.path})`;
          inputLabel.appendChild(locIndicator);
        }
        installWsNotificationHandler<BackingFileUpdated>('backing_file_update', ({ contents }) => modalEnv.setLocalState(contents));
      } else {
        shouldTryInitializingWorkspace = true;
      }

      const onChange = (newValue: string, adjusters?: LocationAdjuster[]) => {
        if (!activeWorkspace || activeWorkspace.activeFileIsTempFile()) {
          settings.setEditorContents(newValue);
        }
        if (activeWorkspace) {
          activeWorkspace.onActiveFileChange(newValue);
        }
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

      const modalEnvHolder: {
        setEnv: (env: ModalEnv) => void,
        cachedEnv: ModalEnv | null,
        setRecv: (recv: (env: ModalEnv) => void) => void,
        cachedRecv: ((env: ModalEnv) => void) | null,
       } = {
        cachedEnv: null,
        cachedRecv: null,
        setEnv: (env) => {
          modalEnvHolder.cachedEnv = env;
          if (modalEnvHolder.cachedRecv) {
            modalEnvHolder.cachedRecv(env);
          }
        },
        setRecv: (recv) => {
          modalEnvHolder.cachedRecv = recv;
          if (modalEnvHolder.cachedEnv) {
            recv(modalEnvHolder.cachedEnv);
          }
        },
      }


      const loadSavedWindows = () => {
        setTimeout(() => {
          try {
            let windowStates: WindowState[] = settings.getProbeWindowStates();
            windowStates.forEach(state => loadWindowState(modalEnv, state));
          }  catch (e) {
            console.warn('Invalid probe window state?', e);
          }
        }, 300); // JUUUUUUUST in case the stored window state causes issues, this 300ms timeout allows people to click the 'clear state' button
      }

      if (getEditorDefinitionPlace().definedEditors[editorType]) {
        const { preload, init, } = getEditorDefinitionPlace().definedEditors[editorType];
        getEditorDefinitionPlace().loadPreload(preload, () => {
          const res = init(settings.getEditorContents() ?? `// Hello World!\n// Write some code in this field, then right click and select 'Create Probe' to get started\n\n`, onChange, settings.getSyntaxHighlighting());
          setLocalState = res.setLocalState || setLocalState;
          getLocalState = res.getLocalState || getLocalState;
          updateSpanHighlight = res.updateSpanHighlight || updateSpanHighlight;
          registerStickyMarker = res.registerStickyMarker || registerStickyMarker;
          markText = res.markText || markText;
          if (res.registerModalEnv) {
            modalEnvHolder.setRecv(res.registerModalEnv);
          }
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
                { result: { start: 0, end: 0, type: '<ROOT>', depth: 0 }, steps: [] },
                { name: kv.slice(needle.length), },
              );
            }
          });
          activeTextProbeManager = setupTextProbeManager({
            env: modalEnv,
            onFinishedCheckingActiveFile: (res) => {
              if (activeWorkspace) {
                activeWorkspace.onActiveFileChecked(res);
              }
            },
          });

          if (shouldTryInitializingWorkspace) {
            const inputLabel = document.querySelector('#input-header > span') as HTMLSpanElement;
            let cachedLocIndicator: HTMLSpanElement | null = null;
            const getLocIndicator = () => {
              if (!cachedLocIndicator) {
                const locIndicator = document.createElement('span');
                locIndicator.style.marginLeft = '0.25rem';
                locIndicator.classList.add('syntax-string');
                inputLabel.appendChild(locIndicator);
                cachedLocIndicator = locIndicator;
              }
              return cachedLocIndicator

            }
            const onActiveFileChanged = () => {
              if (!activeWorkspace) {
                return;
              }
              if (activeWorkspace.activeFileIsTempFile()) {
                getLocIndicator().innerText = `(Temp files)`;
              } else {
                getLocIndicator().innerText = `(${activeWorkspace.getActiveFile()})`;
              }
            };
            initWorkspace({
              env: modalEnv,
              initialLocalContent: settings.getEditorContents() ?? '',
              setLocalContent: contents => setLocalState(contents),
              onActiveFileChanged,
              getCurrentWindows: getCurrentWindowStates,
              setLocalWindows: (states) => {
                states.forEach(state => loadWindowState(modalEnv, state));
              },
              textProbeManager: activeTextProbeManager,
              notifySomeWorkspacePathChanged: () => notifyLocalChangeListeners(undefined, 'workspace_path_updated'),
            })
              .then((ws) => {
                if (ws) {
                  activeWorkspace = ws;
                  onActiveFileChanged();

                  installWsNotificationHandler<WorkspacePathsUpdated>('workspace_paths_updated', ({ paths }) => {
                    ws.onServerNotifyPathsChanged(paths);
                  });

                  if (ws.activeFileIsTempFile()) {
                    loadSavedWindows();
                  }
                }
              })
              .catch((err) => {
                console.warn('Failed initializing workspace', err);
              })
            ;
          } else {
            loadSavedWindows();
          }
        })

      } else {
        // Throw out to editor selection screen
        location.search = '';
      }

      const activeMarkers: TextMarker[] = [];
      const probeMarkers: ModalEnv['probeMarkers'] = {};
      const updateMarkerDebouncer = spammyOperationDebouncer();
      const updateMarkers = () => {
        updateMarkerDebouncer.submit(() => {
          activeMarkers.forEach(m => m?.clear?.());
          activeMarkers.length = 0;

          const deduplicator = new Set();
          const pendingSources: { [uniqId: string]: string[] } = {};
          const pendingAdders: (() => void)[] = [];
          const filteredAddMarker = (severity: Diagnostic['type'], start: number, end: number, msg: string, source?: string) => {
            const uniqId = [severity, start, end, msg].join(' | ');
            pendingSources[uniqId] = pendingSources[uniqId] ?? [];
            pendingSources[uniqId].push(source ?? '');
            if (deduplicator.has(uniqId)) {
              return;
            }
            deduplicator.add(uniqId);
            pendingAdders.push(() => {
              const sources = [...new Set(pendingSources[uniqId].filter(Boolean))].sort((a, b) => (a < b ? -1 : (a > b) ? 1 : 0));

              const lineStart = (start >>> 12);
              const colStart = start & 0xFFF;
              const lineEnd = (end >>> 12);
              const colEnd = end & 0xFFF;
              activeMarkers.push(markText({
                severity: `${severity}`.toLocaleLowerCase('en-GB'),
                lineStart, colStart, lineEnd, colEnd, message: msg,
                source: sources.length === 0 ? undefined: sources.join(', ') }));
            })

          }
          Object.values(probeMarkers).forEach(arr => (Array.isArray(arr) ? arr : arr()).forEach(({ type, start, end, msg, source }) => filteredAddMarker(type, start, end, msg, source)));
          pendingAdders.forEach(pa => pa());
        });
      };

      const setupSimpleCheckbox = (input: HTMLInputElement, initial: boolean, update: (checked: boolean) => void) => {
        input.checked = initial;
        input.oninput = () => { update(input.checked); notifyLocalChangeListeners(); }
      };
      setupSimpleCheckbox(uiElements.captureStdoutCheckbox, settings.shouldCaptureStdio(), cb => settings.setShouldCaptureStdio(cb));
      setupSimpleCheckbox(uiElements.captureTracesCheckbox, settings.shouldCaptureTraces(), cb => settings.setShouldCaptureTraces(cb));
      setupSimpleCheckbox(uiElements.duplicateProbeCheckbox, settings.shouldDuplicateProbeOnAttrClick(), cb => settings.setShouldDuplicateProbeOnAttrClick(cb));
      setupSimpleCheckbox(uiElements.showAllPropertiesCheckbox, settings.shouldShowAllProperties(), cb => settings.setShouldShowAllProperties(cb));
      setupSimpleCheckbox(uiElements.groupPropertiesByAspectCheckbox, settings.shouldGroupPropertiesByAspect(), cb => settings.setShouldGroupPropertiesByAspect(cb));
      setupSimpleCheckbox(uiElements.autoShortenPropertyNamesCheckbox, settings.shouldAutoShortenPropertyNames(), cb => settings.setShouldAutoShortenPropertyNames(cb));

      const setupSimpleSelector = (input: HTMLSelectElement, initial: string, update: (val: string) => void) => {
        input.value = initial;
        input.oninput = () => { update(input.value); notifyLocalChangeListeners(); };
      };
      setupSimpleSelector(uiElements.astCacheStrategySelector, settings.getAstCacheStrategy(), cb => settings.setAstCacheStrategy(cb));
      setupSimpleSelector(uiElements.positionRecoverySelector, settings.getPositionRecoveryStrategy(), cb => settings.setPositionRecoveryStrategy(cb));
      setupSimpleSelector(uiElements.locationStyleSelector, `${settings.getLocationStyle()}`, cb => settings.setLocationStyle(cb as TextSpanStyle));
      setupSimpleSelector(uiElements.textprobeStyleSelector, `${settings.getTextProbeStyle()}`, cb => settings.setTextProbeStyle(cb as TextProbeStyle));

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

      configureCheckboxWithHiddenCheckbox(
        // Outer
        {
          checkbox: uiElements.captureTracesCheckbox,
          initiallyChecked: settings.shouldCaptureTraces(),
          onChange: (checked) => {
            settings.setShouldCaptureTraces(checked);
            notifyLocalChangeListeners();
          },
        },
        // Hidden
        {
          checkbox: uiElements.autoflushTracesCheckbox,
          initiallyChecked: settings.shouldAutoflushTraces(),
          onChange: (checked) => {
            settings.setShouldAutoflushTraces(checked);
            notifyLocalChangeListeners();
          },
          container: uiElements.autoflushTracesContainer,
        }
      )

      const statCollectorImpl = new StatisticsCollectorImpl();
      if (location.search.includes('debug=true')) {
        document.getElementById('secret-debug-panel')!.style.display = 'block';
      }

      // const testManager = createTestManager(req => performRpcQuery(wsHandler, req));
      const createJobId: ModalEnv['createJobId'] = (updateHandler) => {
        const id = ++jobIdGenerator;
        jobUpdateHandlers[id] = updateHandler;
        return id;
      };
      const testManager = createTestManager(() => modalEnv, createJobId);
      let jobIdGenerator = 0;
      const createCullingTaskSubmitter = createCullingTaskSubmitterFactory(changeBufferTime);
      const modalEnv: ModalEnv = {
        showWindow,
        performTypedRpc: (req) => wsHandler.sendRpc(req),
        createParsingRequestData: () => {
          let src: ParsingRequestData['src'];
          if (activeWorkspace && !activeWorkspace.activeFileIsTempFile()) {
            src = { type: 'workspacePath', value: activeWorkspace.getActiveFile() }
          } else {
            src = { type: 'text', value: getLocalState() }
          }
          return {
            posRecovery: uiElements.positionRecoverySelector.value as any,
            cache: uiElements.astCacheStrategySelector.value as any,
            src,
            stdout: settings.shouldCaptureStdio(),
            mainArgs: settings.getMainArgsOverride() ?? undefined,
            tmpSuffix: settings.getCurrentFileSuffix(),
          };
        },
        probeMarkers, onChangeListeners, themeChangeListeners, updateMarkers,
        themeIsLight: () => settings.isLightTheme(),
        getLocalState: () => getLocalState(),
        setLocalState: (newVal) => setLocalState(newVal),
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
        createCullingTaskSubmitter,
        testManager,
        createJobId,
        getGlobalModalEnv: () => modalEnv,
        minimize: (data) => {
          const miniProbe = createMinimizedProbeModal(modalEnv, data.locator, data.property, data.nested, {
            showDiagnostics: data.showDiagnostics
          });
          uiElements.minimizedProbeArea.appendChild(miniProbe.ui);
        },
        workerProcessCount,
       };
       modalEnvHolder.setEnv(modalEnv);

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
          workerProcessCount,
        );
      };

      showVersionInfo(uiElements.versionInfo, hash, clean, buildTimeSeconds, disableVersionCheckerByDefault, modalEnv.performTypedRpc);

      const deferLspToBackend = location.search.includes('debug=true');
      window.HandleLspLikeInteraction = async (type, pos) => {
        switch (type) {
          case 'hover': {
            if (activeTextProbeManager) {
              const res = await activeTextProbeManager.hover(pos.line, pos.column);
              if (res) {
                return res;
              }
            }
            if (!deferLspToBackend) {
              return null;
            }
            const req: HoverReq = {
              type: 'ide:hover',
              src: modalEnv.createParsingRequestData(),
              line: pos.line,
              column: pos.column,
            };
            const result = await modalEnv.performTypedRpc<HoverReq, HoverRes>(req);
            if (!result.lines) {
              return null;
            }

            const ret: any = [];
            result.lines.forEach((line) => {
              ret.push(...line.split('\n').map(value => ({ value: value.trim() })));
            });
            return { contents: ret };
          }

          case 'complete': {
            window.OnCompletionItemFocused = null;
            window.OnCompletionItemListClosed = null;
            if (activeTextProbeManager) {
              const res = await activeTextProbeManager.complete(pos.line, pos.column);
              if (res) {
                return { suggestions: res };
              }
            }
            if (!deferLspToBackend) {
              return null;
            }
            const req: CompleteReq = {
              type: 'ide:complete',
              src: modalEnv.createParsingRequestData(),
              line: pos.line,
              column: pos.column,
            };
            const result = await modalEnv.performTypedRpc<CompleteReq, CompleteRes>(req);
            if (!result.lines) {
              return null;
            }

            const ret: any = [];
            result.lines.forEach((line) => {
              ret.push({ label: line, insertText: line, kind: 3 })
            });
            return { suggestions: ret };
          }

          default: {
            assertUnreachable(type);
            return null;
          }
        }
      }
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
          case 'worker-status': return displayWorkerStatus(
            modalEnv,
            disabled => uiElements.displayWorkerStatusButton.disabled = disabled,
          );
          case 'syntax-highlighting': return common('syntax-highlighting', uiElements.syntaxHighlightingHelpButton);
          case 'main-args-override': return common('main-args-override', uiElements.mainArgsOverrideHelpButton);
          case 'customize-file-suffix':  return common('customize-file-suffix', uiElements.customFileSuffixHelpButton);
          case 'show-all-properties': return common('show-all-properties', uiElements.showAllPropertiesHelpButton)
          case 'group-properties-by-aspect': return common('group-properties-by-aspect', uiElements.groupPropertiesByAspectHelpButton)
          case 'duplicate-probe-on-attr': return common('duplicate-probe-on-attr', uiElements.duplicateProbeHelpButton)
          case 'capture-stdout': return common('capture-stdout', uiElements.captureStdoutHelpButton);
          case 'capture-traces': return common('capture-traces', uiElements.captureTracesHelpButton);
          case 'location-style': return common('location-style', uiElements.locationStyleHelpButton);
          case 'textprobe-style': return common('textprobe-style', uiElements.textprobeStyleHelpButton);
          case 'auto-shorten-property-names': return common('auto-shorten-property-names', uiElements.autoShortenPropertyNamesHelpButton);
          default: return console.error('Unknown help type', type);
        }
      }
      window.RagQuery = (line, col, autoSelectRoot) => {
        if (autoSelectRoot) {
          const node: TALStep = { type: '<ROOT>', start: (line << 12) + col - 1, end: (line << 12) + col + 1, depth: 0 };
          displayAttributeModal(modalEnv, null, createMutableLocator({ result: node, steps: [] }));
        } else {
          displayRagModal(modalEnv, line, col);

        }
      }
    };
    wsHandler.on('init', initHandler);
    installWsNotificationHandler<Refresh>('refresh', () => {
      console.log('notifying of refresh..');
      notifyLocalChangeListeners(undefined, 'refresh-from-server');
    });
  }

  if (location.search.split(/[?&]/).includes('editor=AST')) {
    initEditor('AST');
  } else {
    initEditor('Monaco');
  }
}

window.initCodeProber = () => {
  (async () => {
    if ((window as any).CPR_REQUEST_HANDLER) {
      // In-browser request handler available, us that instead.
      return doMain({ type: 'local-request-handler', handler: (window as any).CPR_REQUEST_HANDLER as LocalRequestHandler })
    }
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



