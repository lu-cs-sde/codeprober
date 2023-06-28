import getEditorDefinitionPlace from '../model/getEditorDefinitionPlace';
import { createImmutableLocator, createMutableLocator } from '../model/UpdatableNodeLocator';
import settings from '../settings';
import { CancelToken } from './create/showWindow';
import displayAstModal from './popup/displayAstModal';
import UIElements from './UIElements';

const installASTEditor = () => {
  (getEditorDefinitionPlace()).defineEditor(
    'AST',
    () => ({
      script: [],
      style: [],
      predicate: () => true,
    }),
    (value, onChange, initialSyntaxHighlight) => {
      settings.setProbeWindowStates([]);
      // TODO load monaco so that main args override editor works

      const inw = document.getElementById('input-wrapper') as HTMLDivElement;
      inw.classList.add('input-AST');
      inw.innerHTML = '';

      // displayAstModal(
      //   {

      //   }
      // )

      return {
        // setLocalState?: (newValue: string) => void;
        // getLocalState?: () => string;
        // updateSpanHighlight?: (baseHighlight: Span | null, stickyHighlights: StickyHighlight[]) => void;
        // registerStickyMarker?: (initialSpan: Span) => StickyMarker;
        // markText?: TextMarkFn;
        themeToggler: (isLightTheme) => {
          // Do anything?
        },
        syntaxHighlightingToggler: (langId) => {
          // Do nothing
        },

        registerModalEnv: (env) => {
          displayAstModal(
            {
              ...env,
              probeWindowStateSavers: {}, // Disable state saving here
              showWindow: (args) => {
                let lastCancelToken: CancelToken = {};
                const render = () => {
                  lastCancelToken.cancelled = true;
                  lastCancelToken = {};
                  args.render(inw, { cancelToken: lastCancelToken, bringToFront: () => {} });
                };
                render();

                window.onresize = () => args.onFinishedResize?.();
                const uiElements = new UIElements();
                uiElements.settingsHider.addEventListener('click', () => args.onFinishedResize?.());
                uiElements.settingsRevealer.addEventListener('click', () => args.onFinishedResize?.());

                return {
                  bumpIntoScreen: () => {},
                  remove: () => {},
                  getPos: () => ({ x: 0, y: 0, }),
                  getSize: () => ({ width: inw.clientWidth, height: inw.clientHeight }),
                  refresh: render
                };
              },
            },
            null,
            createMutableLocator({
              result: { type: '<ROOT>', start: 0, end: 0, depth: 0 },
              steps: []
            }),
            'downwards',
            {
              hideTitleBar: true,
            }
          );
        },
      }
    },
  );
};


export default installASTEditor;

