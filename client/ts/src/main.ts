import addConnectionCloseNotice from "./ui/addConnectionCloseNotice";
import displayProbeModal from "./ui/popup/displayProbeModal";
import displayRagModal from "./ui/popup/displayRagModal";
import displayHelp from "./ui/popup/displayHelp";
import displayAttributeModal from "./ui/popup/displayAttributeModal";
import settings from './settings';
import StatisticsCollectorImpl from "./model/StatisticsCollectorImpl";
import displayStatistics from "./ui/popup/displayStatistics";

type HandlerFn = (data: { [key: string]: any }) => void;

window.clearPastaSettings = () => {
  settings.set({});
  location.reload();
}

const main = () => {
  function toggleTheme() {
    console.log('Theme not defined for current editor');
  }

  let getLocalState = () => '';
  let updateSpanHighlight = (span: Span | null) => {};
  let rpcQuerySocket: any = null;
  let pastaMode = false;
  const rpcHandlers: { [id: string]: HandlerFn } = {};
  let rpcIdGenerator = 1;
  const performRpcQuery = (props: { [key: string]: any }) => new Promise(async (res, rej) => {
    // console.log('send RPC query:', props);
    const posRecoverySelect = document.getElementById('control-position-recovery-strategy') as HTMLSelectElement;
    const astCacheStrategySelector = document.getElementById('ast-cache-strategy') as HTMLSelectElement;
        const id = rpcIdGenerator++; //  Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
        rpcQuerySocket.send(JSON.stringify({
          id,
          posRecovery: posRecoverySelect.value,
          cache: astCacheStrategySelector.value,
          type: 'query',
          text: getLocalState(),
          stdout: settings.shouldCaptureStdio(),
          query: props
        }));

        const cleanup = () => delete rpcHandlers[id];
        rpcHandlers[id] = ({ error, result }) => {
          // console.log('rpc response:', { error, result });
          cleanup();
          if (error) {
            console.warn('RPC request failed', error);
            rej(error);
          } else {
            res(result);
          }
        };

        setTimeout(() => {
          cleanup();
          rej('Timeout');
        }, 30000);
      });

    const onChangeListeners: ModalEnv['onChangeListeners'] = {};

    const probeWindowStateSavers: { [key: string]: (target: ProbeWindowState[]) => void } = {};
    const triggerWindowSave = () => {
      // console.log('triggerWindowsSave...');
      const states: ProbeWindowState[] = [];
      Object.values(probeWindowStateSavers).forEach(v => v(states));
      // console.log('triggerWindowsSave -->', states);
      settings.setProbeWindowStates(states);
    };

    const notifyLocalChangeListeners = (adjusters?: LocationAdjuster[]) => {
      // Short timeout to easier see changes happening. Remove in prod
      // setTimeout(() => {
        Object.values(onChangeListeners).forEach(l => l(adjusters));
        triggerWindowSave();
      // }, 500);
    }

    function init(editorType: string) {
      if (!location.search) {
        location.search = "editor=" + editorType;
        return;
      }
      document.body.setAttribute('data-theme-light', `${settings.isLightTheme()}`);

      document.getElementById('connections')!.style.display = 'none';

    const socket = new WebSocket(`ws://${location.hostname}:8080`);
    rpcQuerySocket = socket;

    let handlers: { [opName: string]: HandlerFn } = {
      rpc: ({ id, ...res }) => {
        const handler = rpcHandlers[id];
        if (handler) {
          delete rpcHandlers[id];
          handler(res);
        } else {
          console.warn('Received RPC response for', id, ', expected one of', Object.keys(rpcHandlers));
        }
      },
    };

    const rootElem = document.getElementById('root') as HTMLElement;
    const init: HandlerFn = ({ value, parser, version }) => {
      rootElem.style.display = "grid";

      const onChange = (newValue: string, adjusters?: LocationAdjuster[]) => {
        settings.setEditorContents(newValue);
        if (!pastaMode) {
          // ++changeCtr;
          // socket.send(JSON.stringify({
          //   type: 'change',
          //   version: changeCtr,
          //   parser: parserToggler.value,
          //   str: newValue,
          //   mode: 'pretty', // [outputToggler.value, modeTail].filter(Boolean).join(':'),
          // }));
        }
        notifyLocalChangeListeners(adjusters);
      };
      // window.foo

      // parserToggler.disabled = false;
      // parserToggler.value = parser;

      let setLocalState = (value: string) => { };
      let markText: TextMarkFn = () => ({});
      let registerStickyMarker: (initialSpan: Span) => StickyMarker = (initialSpan) => ({
        getSpan: () => initialSpan,
        remove: () => {},
      });

      const darkModeCheckbox  = document.getElementById('control-dark-mode') as HTMLInputElement;
      darkModeCheckbox.checked = !settings.isLightTheme();

      const defineThemeToggler = (cb: (lightTheme: boolean) => void) => {
        darkModeCheckbox.oninput = (e) => {
          let lightTheme = !darkModeCheckbox.checked;
          settings.setLightTheme(lightTheme);
          document.body.setAttribute('data-theme-light', `${lightTheme}`);
          cb(lightTheme);
        }
        cb(settings.isLightTheme());

        // let lightTheme = MiniEditorUtils.getThemeIsLight();
        // window.toggleTheme = () => {
        //   lightTheme = !lightTheme;
        //   MiniEditorUtils.setThemeIsLight(lightTheme);
        //   setThemeButton(lightTheme ? '☀️' : '🌛');
        //   cb(lightTheme);
        // }
        // lightTheme = !lightTheme;
        // window.toggleTheme();
      };

      console.log('definedEditors:', Object.keys(window.definedEditors), '; tag:', editorType);
      if (window.definedEditors[editorType]) {
        const { preload, init, } = window.definedEditors[editorType];
        window.loadPreload(preload, () => {
          const res = init(value, onChange);
          setLocalState = res.setLocalState || setLocalState;
          getLocalState = res.getLocalState || getLocalState;
          updateSpanHighlight = res.updateSpanHighlight || updateSpanHighlight;
          registerStickyMarker = res.registerStickyMarker || registerStickyMarker;
          markText = res.markText || markText;
          if (res.themeToggler) {
            defineThemeToggler(res.themeToggler);
          }
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
        Object.values(probeMarkers).forEach(arr => arr.forEach(({ severity, errStart, errEnd, msg }) => filteredAddMarker(severity, errStart, errEnd, msg)));
      };


      const captureStdoutCheckbox = document.getElementById('control-capture-stdout') as HTMLInputElement;
      captureStdoutCheckbox.checked = settings.shouldCaptureStdio();
      captureStdoutCheckbox.oninput = () => {
        settings.setShouldCaptureStdio(captureStdoutCheckbox.checked);
        notifyLocalChangeListeners();
      }
      const astCacheStrategySelector = document.getElementById('ast-cache-strategy') as HTMLSelectElement;
      astCacheStrategySelector.value = settings.getAstCacheStrategy();
      astCacheStrategySelector.oninput = () => {
        settings.setAstCacheStrategy(astCacheStrategySelector.value);
        notifyLocalChangeListeners();
      }
      const recoveryStrategySelector = document.getElementById('control-position-recovery-strategy') as HTMLSelectElement;
      recoveryStrategySelector.value = settings.getPositionRecoveryStrategy();
      recoveryStrategySelector.oninput = () => {
        settings.setPositionRecoveryStrategy(recoveryStrategySelector.value);
        notifyLocalChangeListeners();
      }
      const duplicateProbeCheckbox = document.getElementById('control-duplicate-probe-on-attr') as HTMLInputElement;
      duplicateProbeCheckbox.oninput = () => {
        settings.setShouldDuplicateProbeOnAttrClick(duplicateProbeCheckbox.checked);
      }
      duplicateProbeCheckbox.checked = settings.shouldDuplicateProbeOnAttrClick();


      const statCollectorImpl = new StatisticsCollectorImpl();
      window.displayProbeStatistics = () => {
        displayStatistics(
          statCollectorImpl,
          disabled => (document.getElementById('display-statistics') as HTMLButtonElement).disabled = disabled,
          newContents => {
            setLocalState(newContents);
            // notifyLocalChangeListeners();
          },
          () => modalEnv.currentlyLoadingModals.size > 0,
        );
      };
      if (location.search.includes('debug=true')) {
        document.getElementById('secret-debug-panel')!.style.display = 'block';
      }

      const modalEnv: ModalEnv = {
        performRpcQuery, probeMarkers, onChangeListeners, updateMarkers,
        getLocalState: () => getLocalState(),
        captureStdout: () => captureStdoutCheckbox.checked,
        duplicateOnAttr: () => duplicateProbeCheckbox.checked,
        registerStickyMarker: (...args) => registerStickyMarker(...args),
        updateSpanHighlight: (hl) => updateSpanHighlight(hl),
        probeWindowStateSavers,
        triggerWindowSave,
        statisticsCollector: statCollectorImpl,
        currentlyLoadingModals: new Set<string>(),
       };

      // const inputHeader = document.getElementById('input-header');
      // onChangeListeners['passive-bg-listener'] = () => {
      //   // If you have more than 100k lines in this editor and the program node starts after that,
      //   // then I don't know what to tell you.
      //   const rootLocator: TypeAtLoc = {
      //     start: 0,
      //     end: (100000 << 12) + 100,
      //     type: 'Program',
      //   };
      //   performRpcQuery({
      //     type: 'query',
      //     // ...span,
      //     text: modalEnv.getLocalState(),
      //     query: {
      //       nodeType: 'Program',
      //       attr: {
      //         name: 'pasta_containingSpansAndNodeTypes',
      //       },
      //       locator: {
      //         root: rootLocator,
      //         result: rootLocator,
      //         steps: []
      //       },
      //     },
      //   }).then((res) => {
      //     console.log('PASSIVE:', res);
      //   });
      // };


      window.displayGeneralHelp = () => displayHelp('general',
        disabled => (document.getElementById('display-help') as HTMLButtonElement).disabled = disabled
        );
      window.displayRecoveryStrategyHelp = () => displayHelp('recovery-strategy',
          disabled => (document.getElementById('control-position-recovery-strategy-help') as HTMLButtonElement).disabled = disabled
      );
      window.displayAstCacheStrategyHelp = () => displayHelp('ast-cache-strategy',
          disabled => (document.getElementById('control-ast-cache-strategy-help') as HTMLButtonElement).disabled = disabled
      );

      // setTimeout(() => {
      //   // window.displayAstCacheStrategyHelp();
      //   // displayProbeModal(modalEnv, { x: 320, y: 240 }, { lineStart: 5, colStart: 1, lineEnd: 7, colEnd: 2 }, 'Program', 'prettyPrint');
      // }, 500);
      setTimeout(() => {
        // displayProbeModal(modalEnv, { x: 320, y: 140 }, { lineStart: 4, colStart: 1, lineEnd: 4, colEnd: 5 }, 'Call', 'prettyPrint');
        // displayAttributeModal(modalEnv, { x: 320, y: 140 }, { lineStart: 4, colStart: 1, lineEnd: 4, colEnd: 5 }, 'Call');

        // displayArgModal(modalEnv, { x: 320, y: 140 }, { lineStart: 4, colStart: 1, lineEnd: 4, colEnd: 5 },
        //   'Call',  { name: 'lookup', args: [{ type: 'java.lang.String', name: 'name', value: '' }]}

        // const call = { type: 'Call', start: (4 << 12) + 1, end: (4 << 12) + 5};
        // displayProbeModal(modalEnv, { x: 320, y: 140 }, { root: call, result: call, steps: [] },
        //   { name: 'lookup', args: [{ type: 'java.lang.String', name: 'name', value: 'Foo' }]}
        // );
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
          const node = { type: '<ROOT>', start: (line << 12) + col - 1, end: (line << 12) + col + 1};
          displayAttributeModal(modalEnv, null, { result: node, steps: [] });
        } else {
          displayRagModal(modalEnv, line, col);

        }
      }
    };
    handlers.init = init;
    handlers['init-pasta'] = () => {
      pastaMode = true;
      delete window.DoAutoComplete;
      // rootElem.style.gridTemplateColumns = '3fr 1fr';
      // handlers.init({ value: '// Hello World!\n\nint main() {\n  print(123);\n  print(456);\n}\n', parser: 'beaver', version: 1 });
      handlers.init({ value: settings.getEditorContents() ?? '// Hello World!\n\class Foo {\n  static void main(String[] args) {\n    System.out.println("Hello World!");\n  }\n}\n', parser: 'beaver', version: 1 });
    };
    handlers.refresh = () => {
      notifyLocalChangeListeners();
    }

    let didReceiveAtLeastOneMessage = false;
    // Listen for messages
    socket.addEventListener('message', function (event) {
      didReceiveAtLeastOneMessage = true;
      // console.log('Message from server ', event.data);
      const parsed = JSON.parse(event.data);
      if (handlers[parsed.type]) {
        handlers[parsed.type](parsed);
      } else {
        console.log('No handler for message', parsed, ', got handlers for', Object.keys(handlers));
      }
    });

    window.DoAutoComplete = (line, col) => {
      return performRpcQuery({
        type: 'complete',
        line,
        col,
        text: getLocalState(),
        // parser: parserToggler.value,
      });
    };

    socket.addEventListener('close', () => {
      // Small timeout to reduce risk of it appearing when navigating away
      setTimeout(() => addConnectionCloseNotice(didReceiveAtLeastOneMessage), 100);
    });
  }

  window.maybeAutoInit = () => {
    // const idx = location.search.indexOf('editor=');
    // const editorId = location.search.slice(idx + 'editor='.length).split('&')[0];
    // if (editorId) {
    //   init(editorId);
    // }
    init('Monaco');
  }
  window.init = init;
}

window.MiniEditorMain = main;
// export default main;
